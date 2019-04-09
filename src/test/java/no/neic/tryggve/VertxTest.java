package no.neic.tryggve;

import org.junit.jupiter.api.Test;
import io.vertx.ext.unit.TestSuite;

class VertxTest {

    @Test
    void test() {
        TestSuite suite = TestSuite.create("the_test_suite");
        suite.beforeEach(context -> {
            // Test case setup
        }).test("my_test_case_1", context -> {
            // Test 1
        }).test("my_test_case_2", context -> {
            // Test 2
        }).test("my_test_case_3", context -> {
            // Test 3
        }).afterEach(context -> {
            // Test case cleanup
        });
    }

}
