package com.wikicoding.custompsqlsink;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.sink.SinkConnector;

import java.util.List;
import java.util.Map;

public class PostgresDebeziumSinkConnector extends SinkConnector {
    private Map<String, String> configProps;

    private static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define("connection.url", ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, "JDBC URL for PostgreSQL")
            .define("connection.user", ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, "Username for PostgreSQL")
            .define("connection.password", ConfigDef.Type.PASSWORD, ConfigDef.Importance.HIGH, "Password for PostgreSQL");

    @Override
    public void start(Map<String, String> map) {
        configProps = map;
    }

    @Override
    public Class<? extends Task> taskClass() {
        return PostgresDebeziumSinkTask.class;
    }

    @Override
    public List<Map<String, String>> taskConfigs(int maxTasks) {
        return List.of(configProps);
    }

    @Override
    public void stop() {

    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public String version() {
        return "1.0";
    }
}
