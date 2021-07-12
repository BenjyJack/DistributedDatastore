## Running Locally

### Prerequisite
Make sure Java 11 is installed

### Running the Hub
1. `cd hub`
2. `gradlew bootRun` // this will run on the default port of 8080

### Starting Each Bookstore
1. `cd datastore`
2. `PORT=8081 ADDRESS=http://localhost:8081 DBFILE=db2 gradlew bootRun` // this will run on port 8081 with db file named `db2`
3. POST to `http://localhost:8081/bookstores` something like this
```json
{
    "name": "bookstore name",
    "phone": "212-555-1212",
    "address": "100 5th Avenue, New York, NY 10001"
}
```