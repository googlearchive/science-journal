package com.google.android.apps.forscience.whistlepunk;

class AlwaysAllowedPolicy implements ProxyRecorderController.BindingPolicy {
    @Override
    public void checkBinderAllowed() {
        // always allowed
    }
}
