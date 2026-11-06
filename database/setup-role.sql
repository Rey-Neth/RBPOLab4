-- Переменные psql: database_name, app_password. Выполняется администратором postgres.
SELECT 'CREATE ROLE survey_lab4_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION'
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'survey_lab4_app') \gexec
ALTER ROLE survey_lab4_app PASSWORD :'app_password';
SELECT format('CREATE DATABASE %I OWNER postgres ENCODING %L TEMPLATE template0', :'database_name', 'UTF8')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'database_name') \gexec
