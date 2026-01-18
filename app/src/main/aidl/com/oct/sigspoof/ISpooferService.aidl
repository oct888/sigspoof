package com.oct.sigspoof;

interface ISpooferService {
    int getServiceVersion() = 0;
    String getConfig() = 1;
    void setConfig(String json) = 2;
    void deleteConfig() = 3;
}
