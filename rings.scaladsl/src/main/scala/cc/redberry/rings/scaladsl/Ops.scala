package cc.redberry.rings.scaladsl

import cc.redberry.rings

class RingOps[E](self: E)(ring: Ring[E]) {
  private def constant(value: Int): E = ring.valueOf(value)

  private def constant(value: Long): E = ring.valueOf(value)

  def +(other: Int): E = ring.add(self, ring valueOf other)

  def +(other: E): E = ring.add(self, other)

  def +=(other: E): E = ring.addMutable(self, other)

  def +=(other: Int): E = ring.addMutable(self, ring valueOf other)

  def -(other: Int): E = ring.subtract(self, ring valueOf other)

  def -(other: E): E = ring.subtract(self, other)

  def -=(other: E): E = ring.subtractMutable(self, other)

  def -=(other: Int): E = ring.subtractMutable(self, ring valueOf other)

  def *(other: Int): E = ring.multiply(self, ring valueOf other)

  def *(other: E): E = ring.multiply(self, other)

  def *=(other: E): E = ring.multiplyMutable(self, other)

  def *=(other: Int): E = ring.multiplyMutable(self, ring valueOf other)

  def /%(other: Int): (E, E) = this./%(ring valueOf other)

  def /%(other: E): (E, E) = {
    val qd = ring.divideAndRemainder(self, other)
    (qd(0), qd(1))
  }

  def %(other: Int): E = ring.remainder(self, ring valueOf other)

  def %(other: E): E = ring.remainder(self, other)

  def /(other: Int): E = ring.divideExact(self, ring valueOf other)

  def /(other: E): E = ring.divideExact(self, other)

  def pow(exponent: Int): E = ring.pow(self, exponent)

  def **(exponent: Int): E = ring.pow(self, exponent)

  def ^(exponent: Int): E = this.**(exponent)

  def unary_- : E = ring.negate(self)

  def unary_-= : E = ring.negateMutable(self)

  def unary_+ : E = ring.copy(self)

  def ++ : E = ring.increment(self)

  def -- : E = ring.decrement(self)

  def gcd(other: E): E = ring.gcd(self, other)

  def <(other: E): Boolean = ring.compare(self, other) < 0

  def <=(other: E): Boolean = ring.compare(self, other) <= 0

  def >(other: E): Boolean = ring.compare(self, other) > 0

  def >=(other: E): Boolean = ring.compare(self, other) >= 0

  def ===(other: Int): Boolean = self == constant(other)

  def ===(other: Long): Boolean = self == constant(other)

  def ===(other: E): Boolean = self == other

  def =!=(other: Int): Boolean = self != constant(other)

  def =!=(other: Long): Boolean = self != constant(other)

  def =!=(other: E): Boolean = self != other

  def show: String = ring show self

  def println(): Unit = scala.Predef.println(show)
}

trait RingSupport[E] {
  def ringEv(ev: E): Ring[E]
}

object RingSupport {
  implicit def polyRingSupport[Poly <: IPolynomial[Poly], E]: RingSupport[Poly] = ev => rings.Rings.PolynomialRing(ev)
}

class PolynomialSetOps[Poly <: IPolynomial[Poly]](self: Poly) {
  def :=(other: Poly): Unit = self.set(other)
}

trait PolynomialSetSyntax {
  implicit def polynomialSetOps[Poly <: IPolynomial[Poly]](poly: Poly): PolynomialSetOps[Poly] = new PolynomialSetOps[Poly](poly)
}

class PolynomialCfOps[Poly <: IPolynomial[Poly], E](self: Poly)(pRing: PolynomialRing[Poly, E]) {
  def lc: E = pRing.lc(self)

  def cc: E = pRing.cc(self)

  def +(other: E): Poly = pRing.add(self, other)

  def -(other: E): Poly = pRing.subtract(self, other)

  def *(other: E): Poly = pRing.multiply(self, other)

  def /(other: E): Poly = pRing.divide(self, other)

  def /%(other: E): (Poly, Poly) = pRing.divideAndRemainder(self, other)
}

class UnivariateOps[Poly <: IUnivariatePolynomial[Poly]](self: Poly)(ring: Ring[Poly]) {
  def <<(offset: Int): Poly = ring valueOf self.copy().shiftLeft(offset)

  def >>(offset: Int): Poly = ring valueOf self.copy().shiftRight(offset)

  def apply(from: Int, to: Int): Poly = ring valueOf self.copy().getRange(from, to)

  def composition(poly: Poly): Poly = ring valueOf self.composition(poly)

  def @@(index: Int): Poly = ring valueOf self.getAsPoly(index)
}

class UnivariateCfOps[Poly <: IUnivariatePolynomial[Poly], E](self: Poly)(pRing: IUnivariateRing[Poly, E]) {
  def at(index: Int): E = pRing.at(self, index)

  def eval(point: E): E = pRing.eval(self, point)

  def eval(point: Int): E = pRing.eval(self, pRing.cfValue(point))
}

class MultivariateOps[Term <: DegreeVector[Term], Poly <: AMultivariatePolynomial[Term, Poly]](self: Poly)(ring: Ring[Poly]) {
  def +(other: Term): Poly = ring valueOf self.copy().add(other)

  def -(other: Term): Poly = ring valueOf self.copy().subtract(other)

  def *(other: Term): Poly = ring valueOf self.copy().multiply(other)

  def /(other: Term): Poly = {
    val r = self.copy().divideOrNull(other)
    if (r == null)
      throw new ArithmeticException(s"not divisible: $self / $other")
    else
      ring valueOf r
  }

  def swapVariables(i: Int, j: Int): Poly = rings.poly.multivar.AMultivariatePolynomial.swapVariables[Term, Poly](self, i, j)
}

class MultivariateCfOps[Term <: DegreeVector[Term], Poly <: AMultivariatePolynomial[Term, Poly], E](self: Poly)(pRing: IMultivariateRing[Term, Poly, E]) {
  def eval(i: Int, value: E): Poly = pRing.eval(self, i, value)

  def eval(subs: (String, E)): Poly = eval(pRing.index(subs._1), subs._2)

  def apply(subs: (String, E)): Poly = eval(subs)

  def degree(variable: String): Int = self.degree(pRing.index(variable))

  def swapVariables(i: String, j: String): Poly = rings.poly.multivar.AMultivariatePolynomial.swapVariables[Term, Poly](self, pRing.index(i), pRing.index(j))
}

class CfOps[E, Poly <: IPolynomial[Poly]](self: E)(ring: PolynomialRing[Poly, E]) {
  def +(poly: Poly): Poly = ring.add(poly, self)

  def -(poly: Poly): Poly = ring.negate(ring.subtract(poly, self))

  def *(poly: Poly): Poly = ring.multiply(poly, self)
}

class IntegerOps[E](self: Int)(ring: Ring[E]) {
  def +(oth: E): E = ring.add(oth, ring valueOf self)

  def -(oth: E): E = ring.negate(ring.subtract(oth, ring valueOf self))

  def *(oth: E): E = ring.multiply(oth, ring valueOf self)
}
