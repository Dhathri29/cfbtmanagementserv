package com.paypal.test.sre.utilities;

import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

public class TestNGListeners implements ITestListener  {

    @Override
    public void onStart(ITestContext context) {
        System.out.println("**************************** Beginning " + context.getName() + " Functional Testing ****************************");
    }

    @Override
    public void onFinish(ITestContext context) {
        System.out.println("**************************** Ending " + context.getName() + " Functional Testing ****************************");
    }

    @Override
    public void onTestStart(ITestResult result) {
        System.out.println("**************************** Beginning Test " + result.getName() + " ****************************");
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        System.out.println("**************************** Test " + result.getName() + " completed successfully. ****************************");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        System.out.println("**************************** Test " + result.getName() + " failed. ****************************");
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        System.out.println("**************************** Test " + result.getName() + " skipped. ****************************");
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        System.out.println("**************************** Test " + result.getName() + " failed but within success percentage. ****************************");
    }
}