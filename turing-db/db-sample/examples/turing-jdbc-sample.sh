#! /bin/bash

./turing-jdbc.sh --site Sample -t Family --include-type-in-id true -z 100 -d com.mysql.jdbc.Driver -c jdbc:mysql://localhost/viglet  -q "select * from vigTerm" -u viglet -p viglet
