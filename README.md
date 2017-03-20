# node.kt

A web framework built in Kotlin that is inspired by Node.js. For more information, see https://pasha13666.github.io/node.kt/

### Getting release

To get binary release see [create-node.kt](https://github.com/Pasha13666/create-node.kt/blob/master/README.md#installing)

### Building

Node.kt uses custom shell script to download depends and build.
```sh
./build.sh compiler   # If you dont have kotlin compiler in $PATH.
./build.sh libs       # Downloda libraries.
./build.sh all        # Build node.kt and modules.
```

### Usage

- Replace node.kt.jar and modules that are downloaded via create-node.kt or
- Start the node.kt manually. ```java -cp "path/to/your.jar:`find dist lib -name '*.jar' -printf ':$p'`" your.main.Class```.
