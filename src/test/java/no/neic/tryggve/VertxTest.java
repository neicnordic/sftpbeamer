package no.neic.tryggve;

import org.junit.jupiter.api.Test;
import io.vertx.ext.unit.TestOptions;
import io.vertx.ext.unit.TestSuite;
import io.vertx.ext.unit.report.ReportOptions;

class VertxTest {

    @Test
    void test() {
        TestSuite suite = TestSuite.create("the_test_suite");
        suite.test("my_test_case", context -> {
            String s = "value";
            context.assertEquals("value", s);
        });
        suite.run(new TestOptions().addReporter(new ReportOptions().setTo("console")));
    }

}
