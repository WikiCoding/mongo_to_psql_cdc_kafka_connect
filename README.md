# Configs

1. Open the `mongosh` and initiate a replica set with

```javascript
rs.initiate({
  _id: "rs0",
  members: [{ _id: 0, host: "mongo:27017" }],
});
```

2. Create the table in psql with `psql -U postgres` and

```bash
\c productsdb

CREATE TABLE products (
    _id TEXT PRIMARY KEY,
    name TEXT
);
```

2. Confirm the available plugins with a GET request to http://localhost:8083/connector-plugins

3. MongoDb source POST request to http://localhost:8083/connectors

```json
{
  "name": "mongodb-connector",
  "config": {
    "connector.class": "io.debezium.connector.mongodb.MongoDbConnector",
    "mongodb.connection.string": "mongodb://mongo:27017/?replicaSet=rs0",
    "mongodb.hosts": "rs0/mongo:27017",
    "mongodb.name": "productdb",
    "mongodb.include.collection.list": "products.products",
    "topic.prefix": "prods",
    "database.history.kafka.bootstrap.servers": "kafka:9092",
    "database.history.kafka.topic": "dbhistory.products"
  }
}
```

4. Psql sink POST request to http://localhost:8083/connectors

```json
{
  "name": "jdbc-sink-connector",
  "config": {
    "connector.class": "io.debezium.connector.jdbc.JdbcSinkConnector",
    "tasks.max": "1",
    "connection.url": "jdbc:postgresql://postgres:5432/productsdb",
    "connection.username": "postgres",
    "connection.password": "postgres",
    "topics": "prods.products.products",
    "auto.create": "true",
    "insert.mode": "upsert",
    "delete.enabled": "false",
    "primary.key.fields": "_id",
    "primary.key.mode": "record_value",
    "schema.evolution": "basic",
    "database.time_zone": "UTC",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false",
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "table.name.format": "products",
    "transforms": "unwrap",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "true",
    "transforms.unwrap.delete.handling.mode": "rewrite"
  }
}
```

# Perform kafka checks

```bash
kafka-topics --bootstrap-server localhost:9092 --list
kafka-console-consumer --bootstrap-server localhost:9092 --topic prods.products.products --from-beginning
```

For more info read this article https://medium.com/@felipeas314/sincronizando-dados-entre-mongodb-e-postgresql-usando-debezium-e-kafka-connect-37443e96f65c
