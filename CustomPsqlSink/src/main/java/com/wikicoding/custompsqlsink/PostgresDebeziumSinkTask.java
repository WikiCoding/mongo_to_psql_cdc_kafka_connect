package com.wikicoding.custompsqlsink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

public class PostgresDebeziumSinkTask extends SinkTask {
    private Connection connection;
    private PreparedStatement upsertStmt;
    private PreparedStatement deleteStmt;
    private final ObjectMapper mapper = new ObjectMapper();

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

                if (valueStruct != null)
                    System.out.println("Value Struct read is: " + valueStruct);

                // Handle deletes (tombstone or value is null)
                if (valueStruct == null) {
                    handleDeleteCase(record);

                    continue;
                }

                // Handle inserts or updates
                String afterJson = (String) valueStruct.get("after");

                if (afterJson == null) {
                    System.out.println("Skipping record with null 'after' field");
                    continue;
                }

                String id = "";
                String name = "";

                JsonNode afterNode = mapper.readTree(afterJson);

                JsonNode idNode = afterNode.path("_id").path("$oid");
                if (!idNode.isMissingNode() && idNode.isTextual()) {
                    id = idNode.asText();
                }

                name = afterNode.path("name").asText();

                // Perform upsert
                upsertStmt.setString(1, id);
                upsertStmt.setString(2, name);
                upsertStmt.executeUpdate();
            } catch (Exception e) {
                throw new ConnectException("Error processing record", e);
            }
        }
    }

    private void handleDeleteCase(SinkRecord record) throws JsonProcessingException, SQLException {
        System.out.println("valueStruct is null");

        Map<String, Object> keyObj = (Map<String, Object>) record.key();

        Map<String, Object> payloadJson = (Map<String, Object>) keyObj.get("payload");
        System.out.println("Got payload: " + payloadJson.toString() + " and it's class name is " + payloadJson.getClass().getName());

        Object id = payloadJson.get("id");
        System.out.println("Got oid: " + id.toString() + " and it's class name is " + id.getClass().getName());

        if (id instanceof String) {
            JsonNode idNode = mapper.readTree((String) id);
            String oid = idNode.path("$oid").asText();
            System.out.println("Extracted $oid: " + oid);

            deleteStmt.setString(1, oid);
            deleteStmt.executeUpdate();
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
