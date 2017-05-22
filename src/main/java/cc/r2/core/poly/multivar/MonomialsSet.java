package cc.r2.core.poly.multivar;

import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author Stanislav Poslavsky
 * @since 1.0
 */
final class MonomialsSet<Monomial extends DegreeVector>
        extends TreeMap<DegreeVector, Monomial>
        implements Iterable<Monomial>, Cloneable {

    MonomialsSet(Comparator<? super DegreeVector> comparator) {
        super(comparator);
    }

    MonomialsSet(SortedMap<DegreeVector, ? extends Monomial> m) {
        super(m);
    }

    @Override
    public Iterator<Monomial> iterator() {
        return values().iterator();
    }

    Monomial add(Monomial term) {return put(term, term);}

    Monomial first() {return firstEntry().getValue();}

    Monomial last() {return lastEntry().getValue();}

    @Override
    @SuppressWarnings("unchecked")
    public MonomialsSet<Monomial> clone() {return (MonomialsSet<Monomial>) super.clone();}
}