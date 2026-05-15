CREATE KEYSPACE IF NOT EXISTS flyway_nc_it
    WITH replication = { 'class': 'SimpleStrategy', 'replication_factor': 1 };
