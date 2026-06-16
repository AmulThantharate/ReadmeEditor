module com.readmeeditor {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires redis.clients.jedis;
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires org.commonmark;
    requires org.commonmark.ext.gfm.tables;
    requires org.commonmark.ext.heading.anchor;
    // jBCrypt jar produces automatic module name 'jbcrypt'
    requires jbcrypt;
    requires jdk.httpserver;
    requires java.desktop;

    exports com.readmeeditor;
    exports com.readmeeditor.config;
    exports com.readmeeditor.controller;
    exports com.readmeeditor.model;
    exports com.readmeeditor.repository;
    exports com.readmeeditor.service;
    exports com.readmeeditor.util;
    exports com.readmeeditor.view;

    opens com.readmeeditor.model to com.fasterxml.jackson.databind;
}
