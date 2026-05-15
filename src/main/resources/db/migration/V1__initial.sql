-- V1__Init_database.sql
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(50) PRIMARY KEY, 
    
    username VARCHAR(255) UNIQUE NOT NULL,
    
    password_hash VARCHAR(255) NOT NULL,
    
    role VARCHAR(50) NOT NULL, 
        created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);