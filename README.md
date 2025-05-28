# Running this app

1. To run this, you just need to run `docker-compose up -d` in your terminal. All the steps are automated to register the connectors, create the tables etc. If you wish you can disable the init containers and do the steps manually according to the Configs section below.
2. Then go to http://localhost:8081, enter the `productsdb` and then the `products` collection and create a new document.
3. Finally visit the postgres container or to the adminer at http://localhost:8082/?pgsql=postgres&username=postgres&db=productsdb&ns=public and you'll see that the record will be inserted into the database.

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
    "topic.prefix": "prods",
    "database.history.kafka.bootstrap.servers": "kafka:9092"
  }
}
```

4. Psql sink POST request to http://localhost:8083/connectors

```json
{
  "name": "jdbc-sink-connector",
  "config": {
    "connector.class": "com.wikicoding.custompsqlsink.PostgresDebeziumSinkConnector",
    "tasks.max": "1",
    "connection.url": "jdbc:postgresql://postgres:5432/productsdb",
    "connection.user": "postgres",
    "connection.password": "postgres",
    "topics": "prods.productsdb.products",
    "auto.create": "true",
    "key.converter": "org.apache.kafka.connect.json.JsonConverter",
    "key.converter.schemas.enable": "false",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "true"
  }
}
```

# Perform kafka checks

```bash
kafka-topics --bootstrap-server localhost:9092 --list
kafka-console-consumer --bootstrap-server localhost:9092 --topic prods.products.products --from-beginning
```

For more info read this article https://medium.com/@felipeas314/sincronizando-dados-entre-mongodb-e-postgresql-usando-debezium-e-kafka-connect-37443e96f65c
