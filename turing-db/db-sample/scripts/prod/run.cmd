@ECHO OFF
CALL env.cmd
CALL turing-db.cmd --class-name com.viglet.turing.connector.db.sample.ext.TurDbExtCustomSample -d org.mariadb.jdbc.Driver -c jdbc:mariadb://mysql-rfam-public.ebi.ac.uk:4497/Rfam --db-username rfamro --api-key 968620e286c3483b829642b7f --site Sample -t Family --include-type-in-id true -z 100 -q "select rfam_id as id, description as title, author as abstract, rfam_id as url, created as publication_date, updated as modification_date from family limit 100"
