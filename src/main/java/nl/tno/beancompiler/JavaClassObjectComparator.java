package nl.tno.beancompiler;

import java.util.Comparator;

/**
 *
 * @author bergtwvd
 */
class JavaClassObjectComparator implements Comparator<JavaClassObject> {

    @Override
    public int compare(JavaClassObject o1, JavaClassObject o2) {
        return o1.getName().compareTo(o2.getName());
    }
}
