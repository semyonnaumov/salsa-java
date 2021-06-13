//import junit.framework.Test;
//
///**
//* Contains tests applicable to all Collection implementations.
//*/
//public class CollectionTest extends JSR166TestCase {
//final CollectionImplementation impl;
//
//        /** Tests are parameterized by a Collection implementation. */
//        CollectionTest(CollectionImplementation impl, String methodName) {
//            super(methodName);
//            this.impl = impl;
//        }
//
//        public static Test testSuite(CollectionImplementation impl) {
//            return newTestSuite
//                (parameterizedTestSuite(CollectionTest.class,
//                                                CollectionImplementation.class,
//                                                impl),
//                         jdk8ParameterizedTestSuite(CollectionTest.class,
//                                                    CollectionImplementation.class,
//                                                    impl));
//        }
//
////     public void testCollectionDebugFail() {
////         fail(impl.klazz().getSimpleName());
////     }
//}
