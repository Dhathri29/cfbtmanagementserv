package com.paypal.sre.cfbt.management.rest.api;

/**
 *
 * This is to represent the allowed actions on the TestPackage
 */
public enum PackageAction {
    DOWNLOAD("DOWNLOAD"), DELETE("DELETE");

    private String action;

    private PackageAction(String action) {
        this.action = action;
    }

    public String getAction() {
        return this.action;
    }

    /**
     * @param actionString the string value for the package action.
     * @return the corresponding {@link PackageAction} type
     */
    public static PackageAction getAction(String actionString) {

        for (PackageAction eachAction : PackageAction.values()) {
            if (eachAction.getAction().equals(actionString)) {
                return eachAction;
            }
        }

        throw new IllegalArgumentException(
                actionString + " is not a valid package action. Allowed values : ['DOWNLOAD', 'DELETE'].");
    }

}
