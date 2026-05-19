rootProject.name = "spring-boot-starter-flyway-nc-build"

include(":spring-boot-starter-flyway-nc-dependencies")
include(":spring-boot-starter-flyway-nc")
include(":spring-boot-starter-flyway-nc-cassandra")
include(":spring-boot-starter-flyway-nc-mongodb")

project(":spring-boot-starter-flyway-nc-dependencies").projectDir = file("bom")
project(":spring-boot-starter-flyway-nc").projectDir = file("starter")
project(":spring-boot-starter-flyway-nc-cassandra").projectDir = file("database/cassandra")
project(":spring-boot-starter-flyway-nc-mongodb").projectDir = file("database/mongodb")
