<p align="center">
  <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# CorDapp Raw Updates demo 

A simple Cordapp to experiment with keeping an external query database in sync using the rawUpdates
API 

## Pre-Requisites

See https://docs.corda.net/getting-set-up.html.

## Quick Start 

Build using gradle, if in a hurry just 

```bash
./gradlew build -x test
```

Deploy to running Corda nodes, e.g. Alice and Bob (note, will need Corda 4.4)

Edit the example config in the `configs` folder and run with 

```bash
java -jar clients/build/libs/clients-0.1.jar configs/example.yaml 
```

This will the `CreateFooFlow`. Each time this runs, on both nodes the `FooTrackerService`
is running  and will record the data it has received to the file `foodata.txt` 
in the nodes root folder. A simple example looks like 

```bash
$ cat foodata.txt 
Thread 1 - Foo #1 from Alice to Bob
Thread 2 - Foo #1 from Alice to Bob
Thread 3 - Foo #1 from Alice to Bob
Thread 4 - Foo #1 from Alice to Bob
Thread 5 - Foo #1 from Alice to Bob
Thread 1 - Foo #2 from Alice to Bob
Thread 2 - Foo #2 from Alice to Bob
Thread 3 - Foo #2 from Alice to Bob
Thread 4 - Foo #2 from Alice to Bob
Thread 5 - Foo #2 from Alice to Bob
```    




