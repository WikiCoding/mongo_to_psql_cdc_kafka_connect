package com.wikicoding.custompsqlsink;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.sink.SinkRecord;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.HashMap;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class PostgresDebeziumSinkTaskTest {
    private PostgresDebeziumSinkTask task;
    private Connection mockConnection;
    private PreparedStatement mockUpsert;
    private PreparedStatement mockDelete;

    @BeforeEach
    public void setUp() throws Exception {
        task = new PostgresDebeziumSinkTask();

        // Mock JDBC objects
        mockConnection = mock(Connection.class);
        mockUpsert = mock(PreparedStatement.class);
        mockDelete = mock(PreparedStatement.class);

        // Inject mocks into task
        task = Mockito.spy(task);
        doReturn(mockConnection).when(task).start(any());

        // Mock behavior
        when(mockConnection.prepareStatement(contains("INSERT"))).thenReturn(mockUpsert);
        when(mockConnection.prepareStatement(contains("DELETE"))).thenReturn(mockDelete);

        // Start task with dummy config (bypasses JDBC)
        task.start(new HashMap<>()); // Will not be used since `start()` is stubbed
    }

    @Test
    public void testPut_withAfterJsonString() throws Exception {
        // Sample JSON string like Debezium might emit (after as JSON string)
        String json = """
        {
          "_id": {"$oid": "abc123"},
          "name": "Test Product"
        }
        """;

        // Create a SinkRecord with value being a Struct with "after" as a JSON string
        Struct valueStruct = new Struct(SchemaBuilder.struct().field("after", Schema.STRING_SCHEMA).build())
                .put("after", json);

        SinkRecord record = new SinkRecord(
                "test-topic", 0, null, null,
                null, valueStruct, 0
        );

        // Setup mocks to simulate upsert behavior
        when(mockUpsert.executeUpdate()).thenReturn(1);

        task.put(Collections.singletonList(record));

        verify(mockUpsert).setString(1, "abc123");
        verify(mockUpsert).setString(2, "Test Product");
        verify(mockUpsert).executeUpdate();
    }
}