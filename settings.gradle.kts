rootProject.name = "flyway-nc-build"

include(":spring-boot-flyway-nc-dependencies")
include(":spring-boot-starter-flyway-nc")
include(":spring-boot-starter-flyway-nc-cassandra")
include(":spring-boot-starter-flyway-nc-cassandra-test")
include(":spring-boot-starter-flyway-nc-mongodb")
include(":spring-boot-starter-flyway-nc-mongodb-test")

project(":spring-boot-flyway-nc-dependencies").projectDir =
    file("platform/spring-boot-flyway-nc-dependencies")
project(":spring-boot-starter-flyway-nc").projectDir =
    file("starter/spring-boot-starter-flyway-nc")
project(":spring-boot-starter-flyway-nc-cassandra").projectDir =
    file("starter/spring-boot-starter-flyway-nc-cassandra")
project(":spring-boot-starter-flyway-nc-cassandra-test").projectDir =
    file("starter/spring-boot-starter-flyway-nc-cassandra-test")
project(":spring-boot-starter-flyway-nc-mongodb").projectDir =
    file("starter/spring-boot-starter-flyway-nc-mongodb")
project(":spring-boot-starter-flyway-nc-mongodb-test").projectDir =
    file("starter/spring-boot-starter-flyway-nc-mongodb-test")
