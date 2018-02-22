package cc.redberry.rings.poly.univar;

import cc.redberry.combinatorics.Combinatorics;
import cc.redberry.rings.IntegersZp;
import cc.redberry.rings.Rational;
import cc.redberry.rings.Rings;
import cc.redberry.rings.bigint.BigInteger;
import cc.redberry.rings.poly.*;
import cc.redberry.rings.poly.Util.Tuple2;
import cc.redberry.rings.poly.multivar.*;
import cc.redberry.rings.poly.univar.HenselLifting.QuadraticLiftAbstract;
import cc.redberry.rings.primes.BigPrimes;
import cc.redberry.rings.primes.SmallPrimes;
import cc.redberry.rings.util.ArraysUtil;

import java.util.List;
import java.util.function.Function;

import static cc.redberry.rings.poly.univar.Conversions64bit.asOverZp64;
import static cc.redberry.rings.poly.univar.Conversions64bit.canConvertToZp64;
import static cc.redberry.rings.poly.univar.DistinctDegreeFactorization.DistinctDegreeFactorization;
import static cc.redberry.rings.poly.univar.EqualDegreeFactorization.CantorZassenhaus;
import static cc.redberry.rings.poly.univar.UnivariateSquareFreeFactorization.SquareFreeFactorization;

/**
 * Factorization of univariate polynomials.
 *
 * @since 1.0
 */
public final class UnivariateFactorization {
    private UnivariateFactorization() {}

    /**
     * Factors univariate {@code poly}.
     *
     * @param poly the polynomial
     * @return factor decomposition
     */
    @SuppressWarnings("unchecked")
    public static <Poly extends IUnivariatePolynomial<Poly>> PolynomialFactorDecomposition<Poly> Factor(Poly poly) {
        if (poly.isOverFiniteField())
            return FactorInGF(poly);
        else if (poly.isOverZ())
            return FactorInZ(poly);
        else if (Util.isOverRationals(poly))
            return FactorInQ((UnivariatePolynomial) poly);
        else if (isOverMultivariate(poly))
            return (PolynomialFactorDecomposition<Poly>) FactorOverMultivariate((UnivariatePolynomial) poly, MultivariateFactorization::Factor);
        else if (isOverUnivariate(poly))
            return (PolynomialFactorDecomposition<Poly>) FactorOverUnivariate((UnivariatePolynomial) poly, MultivariateFactorization::Factor);
        else
            throw new RuntimeException("ring is not supported: " + poly.coefficientRingToString());
    }

    @SuppressWarnings("unchecked")
    static <T extends IUnivariatePolynomial<T>> boolean isOverMultivariate(T poly) {
        return (poly instanceof UnivariatePolynomial && ((UnivariatePolynomial) poly).ring instanceof MultivariateRing);
    }

    @SuppressWarnings("unchecked")
    static <T extends IUnivariatePolynomial<T>> boolean isOverUnivariate(T poly) {
        return (poly instanceof UnivariatePolynomial && ((UnivariatePolynomial) poly).ring instanceof UnivariateRing);
    }

    static <Term extends AMonomial<Term>,
            Poly extends AMultivariatePolynomial<Term, Poly>>
    PolynomialFactorDecomposition<UnivariatePolynomial<Poly>>
    FactorOverMultivariate(UnivariatePolynomial<Poly> poly,
                           Function<Poly, PolynomialFactorDecomposition<Poly>> factorFunction) {
        return factorFunction.apply(AMultivariatePolynomial.asMultivariate(poly, 0, true))
                .mapTo(p -> p.asUnivariateEliminate(0));
    }

    static <uPoly extends IUnivariatePolynomial<uPoly>>
    PolynomialFactorDecomposition<UnivariatePolynomial<uPoly>>
    FactorOverUnivariate(UnivariatePolynomial<uPoly> poly,
                         Function<MultivariatePolynomial<uPoly>, PolynomialFactorDecomposition<MultivariatePolynomial<uPoly>>> factorFunction) {
        return factorFunction.apply(AMultivariatePolynomial.asMultivariate(poly, 1, 0, MonomialOrder.LEX))
                .mapTo(MultivariatePolynomial::asUnivariate);
    }

    /**
     * Factors polynomial over Q
     *
     * @param poly the polynomial over finite field
     * @return irreducible factor decomposition
     */
    public static <E> PolynomialFactorDecomposition<UnivariatePolynomial<Rational<E>>> FactorInQ(UnivariatePolynomial<Rational<E>> poly) {
        Tuple2<UnivariatePolynomial<E>, E> cmd = Util.toCommonDenominator(poly);
        UnivariatePolynomial<E> integral = cmd._1;
        E denominator = cmd._2;
        return Factor(integral)
                .mapTo(p -> Util.asOverRationals(poly.ring, p))
                .addUnit(poly.createConstant(new Rational<>(integral.ring, integral.ring.getOne(), denominator)));
    }

    /** x^n * poly */
    private static final class FactorMonomial<T extends IUnivariatePolynomial<T>> {
        final T theRest, monomial;

        FactorMonomial(T theRest, T monomial) {
            this.theRest = theRest;
            this.monomial = monomial;
        }
    }

    /** factor out common monomial term (x^n) */
    private static <Poly extends IUnivariatePolynomial<Poly>> FactorMonomial<Poly> factorOutMonomial(Poly poly) {
        int i = poly.firstNonZeroCoefficientPosition();

        if (i == 0)
            return new FactorMonomial<>(poly, poly.createOne());
        return new FactorMonomial<>(poly.clone().shiftLeft(i), poly.createMonomial(i));
    }

    /** early check for trivial cases */
    private static <Poly extends IUnivariatePolynomial<Poly>> PolynomialFactorDecomposition<Poly> earlyFactorizationChecks(Poly poly) {
        if (poly.degree() <= 1 || poly.isMonomial())
            return PolynomialFactorDecomposition.of(poly.lcAsPoly(), poly.isMonic() ? poly : poly.clone().monic());

        return null;
    }

    /* =========================================== Factorization in Zp[x] =========================================== */

    /**
     * Factors polynomial over finite field
     *
     * @param poly the polynomial over finite field
     * @return irreducible factor decomposition
     * @see UnivariateSquareFreeFactorization
     * @see DistinctDegreeFactorization
     * @see EqualDegreeFactorization
     */
    public static <Poly extends IUnivariatePolynomial<Poly>> PolynomialFactorDecomposition<Poly> FactorInGF(Poly poly) {
        Util.ensureOverFiniteField(poly);
        if (canConvertToZp64(poly))
            return FactorInGF(asOverZp64(poly)).mapTo(Conversions64bit::convert);

        PolynomialFactorDecomposition<Poly> result = earlyFactorizationChecks(poly);
        if (result != null)
            return result;
        result = PolynomialFactorDecomposition.empty(poly);
        FactorInGF(poly, result);
        return result.setUnit(poly.lcAsPoly());
    }

    /**
     * Factors square-free polynomial over finite field
     *
     * @param poly the square-free polynomial over finite field
     * @return irreducible factor decomposition
     * @see DistinctDegreeFactorization
     * @see EqualDegreeFactorization
     */
    public static <T extends IUnivariatePolynomial<T>> PolynomialFactorDecomposition<T> FactorSquareFreeInGF(T poly) {
        Util.ensureOverFiniteField(poly);
        if (canConvertToZp64(poly))
            return FactorInGF(asOverZp64(poly)).mapTo(Conversions64bit::convert);

        PolynomialFactorDecomposition<T> result = PolynomialFactorDecomposition.empty(poly);
        FactorSquareFreeInGF(poly, 1, result);
        return result;
    }

    private static <T extends IUnivariatePolynomial<T>>
    void FactorSquareFreeInGF(T poly, int exponent, PolynomialFactorDecomposition<T> result) {
        //do distinct-degree factorization
        PolynomialFactorDecomposition<T> ddf = DistinctDegreeFactorization(poly);
        //assertDistinctDegreeFactorization(sqfFactor, ddf);
        for (int j = 0; j < ddf.size(); ++j) {
            //for each distinct-degree factor
            T ddfFactor = ddf.get(j);
            int ddfExponent = ddf.getExponent(j);

            //do equal-degree factorization
            PolynomialFactorDecomposition<T> edf = CantorZassenhaus(ddfFactor, ddfExponent);
            for (T irreducibleFactor : edf.factors)
                //put final irreducible factor into the result
                result.addFactor(irreducibleFactor.monic(), exponent);
        }
    }

    private static <T extends IUnivariatePolynomial<T>> void FactorInGF(T poly, PolynomialFactorDecomposition<T> result) {
        FactorMonomial<T> base = factorOutMonomial(poly);
        if (!base.monomial.isConstant())
            result.addFactor(poly.createMonomial(1), base.monomial.degree());

        //do square-free factorization
        PolynomialFactorDecomposition<T> sqf = SquareFreeFactorization(base.theRest);
        //assert sqf.toPolynomial().equals(base.theRest) : base.toString();
        for (int i = 0; i < sqf.size(); ++i) {
            //for each square-free factor
            T sqfFactor = sqf.get(i);
            int sqfExponent = sqf.getExponent(i);
            FactorSquareFreeInGF(sqfFactor, sqfExponent, result);
        }
    }

    private static <T extends IUnivariatePolynomial<T>>
    void assertDistinctDegreeFactorization(T poly, PolynomialFactorDecomposition<T> factorization) {
        for (int i = 0; i < factorization.factors.size(); i++)
            assert 0 == factorization.factors.get(i).degree() % factorization.exponents.get(i) : "Factor's degree is not divisible by d.d.f. exponent";
        assert poly.equals(factorization.multiplyIgnoreExponents());
    }

    /* =========================================== Factorization in Z[x] =========================================== */


    /** assertion for correct Hensel structure */
    static <T extends IUnivariatePolynomial<T>> void assertHenselLift(QuadraticLiftAbstract<T> lift) {
        assert lift.polyMod().equals(lift.aFactor.clone().multiply(lift.bFactor)) : lift.toString();
        assert (lift.aCoFactor == null && lift.bCoFactor == null)
                || lift.aFactor.clone().multiply(lift.aCoFactor).add(lift.bFactor.clone().multiply(lift.bCoFactor)).isOne() : lift.toString();
    }

    /** cache of references **/
    private static int[][] naturalSequenceRefCache = new int[32][];

    private static int[] createSeq(int n) {
        int[] r = new int[n];
        for (int i = 0; i < n; i++)
            r[i] = i;
        return r;
    }

    /** returns sequence of natural numbers */
    private static int[] naturalSequenceRef(int n) {
        if (n >= naturalSequenceRefCache.length)
            return createSeq(n);
        if (naturalSequenceRefCache[n] != null)
            return naturalSequenceRefCache[n];
        return naturalSequenceRefCache[n] = createSeq(n);
    }

    /** select elements by their positions */
    private static int[] select(int[] data, int[] positions) {
        int[] r = new int[positions.length];
        int i = 0;
        for (int p : positions)
            r[i++] = data[p];
        return r;
    }

    /** reconstruct true factors by enumerating all combinations */
    static PolynomialFactorDecomposition<UnivariatePolynomialZ64> reconstructFactorsZ(
            UnivariatePolynomialZ64 poly,
            PolynomialFactorDecomposition<UnivariatePolynomialZp64> modularFactors) {

        if (modularFactors.isTrivial())
            return PolynomialFactorDecomposition.of(poly);

        UnivariatePolynomialZp64 factory = modularFactors.get(0);

        int[] modIndexes = naturalSequenceRef(modularFactors.size());
        PolynomialFactorDecomposition<UnivariatePolynomialZ64> trueFactors = PolynomialFactorDecomposition.empty(poly);
        UnivariatePolynomialZ64 fRest = poly;
        int s = 1;

        factor_combinations:
        while (2 * s <= modIndexes.length) {
            for (int[] combination : Combinatorics.combinations(modIndexes.length, s)) {
                int[] indexes = select(modIndexes, combination);

                UnivariatePolynomialZp64 mFactor = factory.createConstant(fRest.lc());
                for (int i : indexes)
                    mFactor = mFactor.multiply(modularFactors.get(i));
                UnivariatePolynomialZ64 factor = mFactor.asPolyZSymmetric().primitivePart();

                if (fRest.lc() % factor.lc() != 0 || fRest.cc() % factor.cc() != 0)
                    continue;

                UnivariatePolynomialZp64 mRest = factory.createConstant(fRest.lc() / factor.lc());
                int[] restIndexes = ArraysUtil.intSetDifference(modIndexes, indexes);
                for (int i : restIndexes)
                    mRest = mRest.multiply(modularFactors.get(i));
                UnivariatePolynomialZ64 rest = mRest.asPolyZSymmetric().primitivePart();

                if (MachineArithmetic.safeMultiply(factor.lc(), rest.lc()) != fRest.lc()
                        || MachineArithmetic.safeMultiply(factor.cc(), rest.cc()) != fRest.cc())
                    continue;
                if (rest.clone().multiplyUnsafe(factor).equals(fRest)) {
                    modIndexes = restIndexes;
                    trueFactors.addFactor(factor, 1);
                    fRest = rest.primitivePart();
                    continue factor_combinations;
                }
            }
            ++s;
        }

        if (!fRest.isConstant())
            trueFactors.addFactor(fRest, 1);

        return trueFactors;
    }


    /** reconstruct true factors by enumerating all combinations */
    static PolynomialFactorDecomposition<UnivariatePolynomial<BigInteger>> reconstructFactorsZ(
            UnivariatePolynomial<BigInteger> poly,
            PolynomialFactorDecomposition<UnivariatePolynomial<BigInteger>> modularFactors) {

        if (modularFactors.isTrivial())
            return PolynomialFactorDecomposition.of(poly);

        UnivariatePolynomial<BigInteger> factory = modularFactors.get(0);

        int[] modIndexes = naturalSequenceRef(modularFactors.size());
        PolynomialFactorDecomposition<UnivariatePolynomial<BigInteger>> trueFactors = PolynomialFactorDecomposition.empty(poly);
        UnivariatePolynomial<BigInteger> fRest = poly;
        int s = 1;

        factor_combinations:
        while (2 * s <= modIndexes.length) {
            for (int[] combination : Combinatorics.combinations(modIndexes.length, s)) {
                int[] indexes = select(modIndexes, combination);

                UnivariatePolynomial<BigInteger> mFactor = factory.createConstant(fRest.lc());
                for (int i : indexes)
                    mFactor = mFactor.multiply(modularFactors.get(i));
                UnivariatePolynomial<BigInteger> factor = UnivariatePolynomial.asPolyZSymmetric(mFactor).primitivePart();

                if (!fRest.lc().remainder(factor.lc()).isZero() || !fRest.cc().remainder(factor.cc()).isZero())
                    continue;

                UnivariatePolynomial<BigInteger> mRest = factory.createConstant(fRest.lc().divide(factor.lc()));
                int[] restIndexes = ArraysUtil.intSetDifference(modIndexes, indexes);
                for (int i : restIndexes)
                    mRest = mRest.multiply(modularFactors.get(i));
                UnivariatePolynomial<BigInteger> rest = UnivariatePolynomial.asPolyZSymmetric(mRest).primitivePart();

                if (!factor.lc().multiply(rest.lc()).equals(fRest.lc())
                        || !factor.cc().multiply(rest.cc()).equals(fRest.cc()))
                    continue;
                if (rest.clone().multiply(factor).equals(fRest)) {
                    modIndexes = restIndexes;
                    trueFactors.addFactor(factor, 1);
                    fRest = rest.primitivePart();
                    continue factor_combinations;
                }
            }
            ++s;
        }

        if (!fRest.isConstant())
            trueFactors.addFactor(fRest, 1);

        return trueFactors;
    }

    private static final double
            MAX_PRIME_GAP = 382,
            MIGNOTTE_MAX_DOUBLE_32 = (2.0 * Integer.MAX_VALUE) - 10 * MAX_PRIME_GAP,
            MIGNOTTE_MAX_DOUBLE_64 = MIGNOTTE_MAX_DOUBLE_32 * MIGNOTTE_MAX_DOUBLE_32;

    private static final int
            LOWER_RND_MODULUS_BOUND = 1 << 24,
            UPPER_RND_MODULUS_BOUND = 1 << 30;

    private static int randomModulusInf() {
        return LOWER_RND_MODULUS_BOUND + PrivateRandom.getRandom().nextInt(UPPER_RND_MODULUS_BOUND - LOWER_RND_MODULUS_BOUND);
    }

    private static int next32BitPrime(int val) {
        if (val < 0) {
            long l = BigPrimes.nextPrime(Integer.toUnsignedLong(val));
            assert MachineArithmetic.fits32bitWord(l);
            return (int) l;
        } else
            return SmallPrimes.nextPrime(val);
    }

    final static int N_MODULAR_FACTORIZATION_TRIALS = 2;

    /**
     * Factors primitive square-free polynomial using Hensel lifting
     *
     * @param poly Z[x] primitive square-free polynomial
     */
    static PolynomialFactorDecomposition<UnivariatePolynomial<BigInteger>> FactorSquareFreeInZ0(UnivariatePolynomial<BigInteger> poly) {
        assert poly.content().isOne();
        assert poly.lc().signum() > 0;

        BigInteger bound2 = BigInteger.TWO.multiply(UnivariatePolynomial.mignotteBound(poly)).multiply(poly.lc().abs());
        if (bound2.compareTo(MachineArithmetic.b_MAX_SUPPORTED_MODULUS) < 0) {
            PolynomialFactorDecomposition<UnivariatePolynomialZ64> tryLong = FactorSquareFreeInZ0(UnivariatePolynomial.asOverZ64(poly));
            if (tryLong != null)
                return convertFactorizationToBigIntegers(tryLong);
        }

        // choose prime at random
        long modulus = -1;
        UnivariatePolynomialZp64 moduloImage;
        PolynomialFactorDecomposition<UnivariatePolynomialZp64> lModularFactors = null;

        for (int attempt = 0; attempt < N_MODULAR_FACTORIZATION_TRIALS; attempt++) {
            long tmpModulus;
            do {
                tmpModulus = SmallPrimes.nextPrime(randomModulusInf());
                moduloImage = UnivariatePolynomial.asOverZp64(poly.setRing(new IntegersZp(tmpModulus)));
            }
            while (moduloImage.cc() == 0 || moduloImage.degree() != poly.degree() || !UnivariateSquareFreeFactorization.isSquareFree(moduloImage));

            // do modular factorization
            PolynomialFactorDecomposition<UnivariatePolynomialZp64> tmpFactors = FactorInGF(moduloImage.monic());
            if (tmpFactors.size() == 1)
                return PolynomialFactorDecomposition.of(poly);

            if (lModularFactors == null || lModularFactors.size() > tmpFactors.size()) {
                lModularFactors = tmpFactors;
                modulus = tmpModulus;
            }

            if (lModularFactors.size() <= 3)
                break;
        }

        List<UnivariatePolynomial<BigInteger>> modularFactors = HenselLifting.liftFactorization(BigInteger.valueOf(modulus), bound2, poly, lModularFactors.factors);
        assert modularFactors.get(0).ring.cardinality().compareTo(bound2) >= 0;
        return reconstructFactorsZ(poly, PolynomialFactorDecomposition.of(modularFactors));
    }

    /** machine integers -> BigIntegers */
    static <T extends AUnivariatePolynomial64<T>> PolynomialFactorDecomposition<UnivariatePolynomial<BigInteger>>
    convertFactorizationToBigIntegers(PolynomialFactorDecomposition<T> decomposition) {
        return decomposition.mapTo(AUnivariatePolynomial64::toBigPoly);
    }

    /** determines the lower bound for the possible modulus for Zp trials */
    private static int chooseModulusLowerBound(double bound2) {
        long infinum;
        if (bound2 < MIGNOTTE_MAX_DOUBLE_32) {
            // we can use single 32-bit modulus
            infinum = (long) bound2;
        } else if (bound2 < MIGNOTTE_MAX_DOUBLE_64) {
            // we still can use machine-size words
            // two options possible:
            // 1) use 64-bit "(long) bound2" as modulus (which is more than 32-bit and thus slow)
            // 2) use 32-bit modulus and perform a single Hensel step
            // we use 2) option

            infinum = (long) Math.sqrt(bound2);
        } else {
            // coefficient bound is large -> we anyway need several Hensel steps
            // so we just pick 32-bit prime at random and must use BigInteger arithmetics

            throw new IllegalArgumentException();
            //infinum = randomModulusInf();
        }

        assert infinum < MIGNOTTE_MAX_DOUBLE_32;
        return (int) infinum;
    }

    /**
     * Factors square-free polynomial using Hensel lifting
     *
     * @param poly Z[x] square-free polynomial
     */
    static PolynomialFactorDecomposition<UnivariatePolynomialZ64> FactorSquareFreeInZ0(UnivariatePolynomialZ64 poly) {
        assert poly.content() == 1;
        assert poly.lc() > 0;

        long lc = poly.lc();
        double bound2 = 2.0 * poly.mignotteBound() * Math.abs(lc);
        // choose prime at random
        int trial32Modulus = chooseModulusLowerBound(bound2) - 1;
        long modulus;
        UnivariatePolynomialZp64 moduloImage;
        do {
            trial32Modulus = next32BitPrime(trial32Modulus + 1);
            modulus = Integer.toUnsignedLong(trial32Modulus);
            moduloImage = poly.modulus(modulus, true);
        } while (!UnivariateSquareFreeFactorization.isSquareFree(moduloImage));

        // do Hensel lifting
        // determine number of Hensel steps
        int henselIterations = 0;
        long liftedModulus = modulus;
        while (liftedModulus < bound2) {
            if (MachineArithmetic.isOverflowMultiply(liftedModulus, liftedModulus))
                return null;
            liftedModulus = MachineArithmetic.safeMultiply(liftedModulus, liftedModulus);
            ++henselIterations;
        }

        // do modular factorization
        PolynomialFactorDecomposition<UnivariatePolynomialZp64> modularFactors = FactorInGF(moduloImage.monic());

        // actual lift
        if (henselIterations > 0)
            modularFactors = PolynomialFactorDecomposition.of(HenselLifting.liftFactorization(
                    modulus, liftedModulus, henselIterations, poly, modularFactors.factors, true))
                    .addUnit(modularFactors.unit.setModulus(liftedModulus));

        //reconstruct true factors
        return reconstructFactorsZ(poly, modularFactors);
    }

    public static <PolyZ extends IUnivariatePolynomial<PolyZ>> PolynomialFactorDecomposition<PolyZ> FactorSquareFreeInZ(PolyZ poly) {
        ensureIntegersDomain(poly);
        if (poly.degree() <= 1 || poly.isMonomial())
            return PolynomialFactorDecomposition.of(
                    poly.isMonic() ? poly.createOne() : poly.contentAsPoly(),
                    poly.isMonic() ? poly : poly.clone().primitivePart());

        PolyZ content = poly.contentAsPoly();
        if (poly.signumOfLC() < 0)
            content = content.negate();
        return FactorSquareFreeInZ0(poly.clone().divideByLC(content)).setUnit(content);
    }

    @SuppressWarnings("unchecked")
    private static <PolyZ extends IUnivariatePolynomial<PolyZ>> PolynomialFactorDecomposition<PolyZ> FactorSquareFreeInZ0(PolyZ poly) {
        if (poly instanceof UnivariatePolynomialZ64)
            return (PolynomialFactorDecomposition<PolyZ>) FactorSquareFreeInZ0((UnivariatePolynomialZ64) poly);
        else
            return (PolynomialFactorDecomposition<PolyZ>) FactorSquareFreeInZ0((UnivariatePolynomial) poly);
    }

    private static void ensureIntegersDomain(IUnivariatePolynomial poly) {
        if (poly instanceof UnivariatePolynomialZ64 ||
                (poly instanceof UnivariatePolynomial && ((UnivariatePolynomial) poly).ring.equals(Rings.Z)))
            return;
        throw new IllegalArgumentException("Not an integers ring for factorization in Z[x]");
    }

    /**
     * Factors polynomial in Z[x].
     *
     * @param poly the polynomial
     * @return factor decomposition
     * @see #FactorInGF(IUnivariatePolynomial)
     * @see HenselLifting
     */
    public static <Poly extends IUnivariatePolynomial<Poly>> PolynomialFactorDecomposition<Poly> FactorInZ(Poly poly) {
        ensureIntegersDomain(poly);
        if (poly.degree() <= 1 || poly.isMonomial())
            return PolynomialFactorDecomposition.of(
                    poly.isMonic() ? poly.createOne() : poly.contentAsPoly(),
                    poly.isMonic() ? poly : poly.clone().primitivePart());

        PolynomialFactorDecomposition<Poly> result = PolynomialFactorDecomposition.empty(poly);
        Poly content = poly.contentAsPoly();
        if (poly.signumOfLC() < 0)
            content = content.negate();
        FactorInZ(poly.clone().divideByLC(content), result);
        return result.setUnit(content);
    }

    private static <T extends IUnivariatePolynomial<T>> void FactorInZ(T poly, PolynomialFactorDecomposition<T> result) {
        FactorMonomial<T> base = factorOutMonomial(poly);
        if (!base.monomial.isConstant())
            result.addFactor(poly.createMonomial(1), base.monomial.degree());

        //do square-free factorization
        PolynomialFactorDecomposition<T> sqf = SquareFreeFactorization(base.theRest);
        for (int i = 0; i < sqf.size(); ++i) {
            //for each square-free factor
            T sqfFactor = sqf.get(i);
            int sqfExponent = sqf.getExponent(i);

            //do distinct-degree factorization
            PolynomialFactorDecomposition<T> cz = FactorSquareFreeInZ0(sqfFactor);
            //do equal-degree factorization
            for (T irreducibleFactor : cz.factors)
                //put final irreducible factor into the result
                result.addFactor(irreducibleFactor, sqfExponent);
        }
    }
}
