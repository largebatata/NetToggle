package com.largebatata.nettoggle;

interface IFirewallService {
    String getState(String packageName) = 1;
    String setBlocked(String packageName, boolean blocked) = 2;
    String[] getStates(in String[] packageNames) = 3;
    void destroy() = 16777114;
}
