package com.cifertech.multitenant.services;

public interface MultitenantContext {
    String getTenant();

    String getDataSource();

}
