package com.wikicoding.custompsqlsink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.Map;

public class PostgresDebeziumSinkTask extends SinkTask {
    private Connection connection;
    private PreparedStatement upsertStmt;
    private PreparedStatement deleteStmt;
    private ObjectMapper mapper = new ObjectMapper();

    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;

    @Override
    public void start(Map<String, String> props) {
        jdbcUrl = "jdbc:postgresql://postgres:5432/productsdb";
        jdbcUser = "postgres";
        jdbcPassword = "postgres";

        try {
            connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);

            upsertStmt = connection.prepareStatement(
                    "INSERT INTO products (_id, name) VALUES (?, ?) " +
                            "ON CONFLICT (_id) DO UPDATE SET name = EXCLUDED.name");

            deleteStmt = connection.prepareStatement(
                    "DELETE FROM products WHERE _id = ?"
            );
        } catch (Exception e) {
            throw new ConnectException("Failed to connect to PostgreSQL", e);
        }
    }

    @Override
    public void put(Collection<SinkRecord> records) {
        for (SinkRecord record : records) {
            try {
                Struct valueStruct = (Struct) record.value();
                if (valueStruct == null) {
                    System.out.println("valueStruct is null");

                    Object keyObj = record.key();
                    if (keyObj instanceof Map) {
                        Map<String, Object> keyMap = (Map<String, Object>) keyObj;
                        Object rawIdJson = keyMap.get("id");

                        if (rawIdJson != null) {
                            JsonNode idNode = mapper.readTree(rawIdJson.toString()); // rawIdJson is a JSON string
                            String id = idNode.path("$oid").asText();

                            System.out.println("Found id: " + id);

                            deleteStmt.setString(1, id);
                            deleteStmt.executeUpdate();
                        } else {
                            System.out.println("id field in keyMap is null");
                        }
                    } else {
                        System.out.println("record.key() is not a Map, it is: " + (keyObj != null ? keyObj.getClass().getName() : "null"));
                    }

                    continue;
                }

                Object afterObj = valueStruct.get("after");
                System.out.println("afterObj class: " + (afterObj == null ? "null" : afterObj.getClass().getName()));

                if (afterObj == null) {
                    continue;
                }

                if (afterObj instanceof Struct) {
                    Struct after = (Struct) afterObj;

                    // Extract _id which can be a nested Struct with $oid
                    String id = "";
                    Object idObj = after.get("_id");
                    if (idObj instanceof Struct) {
                        Struct idStruct = (Struct) idObj;
                        id = idStruct.getString("$oid");
                    } else if (idObj instanceof String) {
                        id = (String) idObj;
                    }

                    String name = after.getString("name");

                    upsertStmt.setString(1, id);
                    upsertStmt.setString(2, name);
                    upsertStmt.executeUpdate();

                } else if (afterObj instanceof String) {
                    // after is JSON string, parse it
                    String afterJson = (String) afterObj;
                    JsonNode afterNode = mapper.readTree(afterJson);

                    // Extract _id.$oid properly
                    String id = "";
                    JsonNode idNode = afterNode.path("_id");
                    if (!idNode.isMissingNode()) {
                        JsonNode oidNode = idNode.path("$oid");
                        if (!oidNode.isMissingNode() && oidNode.isTextual()) {
                            id = oidNode.asText();
                        }
                    }

                    String name = afterNode.path("name").asText();

                    upsertStmt.setString(1, id);
                    upsertStmt.setString(2, name);
                    upsertStmt.executeUpdate();

                } else {
                    // unexpected type, skip or log
                    continue;
                }

            } catch (Exception e) {
                throw new ConnectException("Error processing record", e);
            }
        }
    }



    @Override
    public void stop() {
        try {
            if (upsertStmt != null) upsertStmt.close();
            if (connection != null) connection.close();
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public String version() {
        return "1.0";
    }
}
