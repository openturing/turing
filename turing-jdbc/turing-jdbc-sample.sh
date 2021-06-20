#! /bin/bash

./turing-jdbc.sh -d com.mysql.jdbc.Driver -c jdbc:mysql://localhost/viglet  -q "select * from vigTerm" -u viglet -p viglet
