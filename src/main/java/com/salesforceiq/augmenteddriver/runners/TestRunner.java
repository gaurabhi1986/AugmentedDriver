package com.salesforceiq.augmenteddriver.runners;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.salesforceiq.augmenteddriver.integrations.IntegrationFactory;
import com.salesforceiq.augmenteddriver.util.Util;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import ru.yandex.qatools.allure.junit.AllureRunListener;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * Knows how to run one test.
 */
public class TestRunner implements Callable<AugmentedResult> {
    private static final Log LOG = LogFactory.getLog(TestRunner.class);

    private final Method test;
    private final ByteArrayOutputStream outputStream;
    private final String nameAppender;
    private final IntegrationFactory integrationFactory;
    private final boolean retry;

    @Inject
    public TestRunner(@Assisted Method test,
                      @Assisted String nameAppender,
                      @Assisted boolean retry,
                      ByteArrayOutputStream outputStream,
                      IntegrationFactory integrationFactory) {
        this.test = Preconditions.checkNotNull(test);
        this.nameAppender = Preconditions.checkNotNull(nameAppender);
        this.outputStream = Preconditions.checkNotNull(outputStream);
        this.retry = retry;
        this.integrationFactory = Preconditions.checkNotNull(integrationFactory);
    }

    /**
     * Wrapper over JUniteCore that runs one test.
     *
     * @return the result of the test.
     * @throws Exception if there was an exception running the test.
     */
    @Override
    public AugmentedResult call() throws Exception {
        JUnitCore jUnitCore = getJUnitCore();
        String testName = String.format("%s#%s", test.getDeclaringClass().getCanonicalName(), test.getName());
        long start = System.currentTimeMillis();
        try {
            LOG.info(String.format("STARTING Test %s", testName));
            // HACK since for TestSuiteRunner we want to retry, and for TestMethodRunner we don't want to
            // The other way would be a RETRY on augmented.properties, but this is independent of the configuration of
            // the test.
            if (retry) {
                TestRunnerRetryingRule.retry();
            }
            Result result = jUnitCore.run(Request.method(test.getDeclaringClass(), test.getName()));
            LOG.info(String.format("FINSHED Test %s in %s, result %s", testName,
                    Util.TO_PRETTY_FORMAT.apply(System.currentTimeMillis() - start), result.wasSuccessful()? "SUCCEEDED" : "FAILED"));
            return new AugmentedResult(testName, result, outputStream);
        } finally {
            outputStream.close();
        }
    }

    private JUnitCore getJUnitCore() throws FileNotFoundException {
        JUnitCore jUnitCore = new JUnitCore();
        if (integrationFactory.teamCity().isEnabled()) {
            jUnitCore.addListener(integrationFactory.teamCity().getReporter(outputStream, nameAppender));
        }

        if (integrationFactory.allure().isEnabled()) {
            jUnitCore.addListener(new AllureRunListener());
        }

        if (integrationFactory.jenkins().isEnabled()) {
            jUnitCore.addListener(integrationFactory.jenkins().getReporter(test, nameAppender));
        }
        return jUnitCore;
    }
}
