Viglet Turing ES - Distribution
================================

Viglet Turing ES is an open-source enterprise search platform powered by AI
that enables semantic navigation, content indexing, and intelligent search.

Prerequisites
-------------
- Java 21 or later (set JAVA_HOME and add to PATH)

Directory Structure
-------------------
  turing-install/
  ├── bin/                              Start scripts
  │   └── turing.sh/.bat               Start Turing ES
  ├── server/                           Application server
  │   ├── viglet-turing.jar             Main application (Spring Boot)
  │   └── viglet-turing.properties      Configuration (edit this!)
  └── utils/                            Utilities
      └── solr/                         Solr collection templates
          └── 2026.1/turing/
              ├── en/                   English configuration
              └── pt/                   Portuguese configuration

Quick Start
-----------
1. Edit server/viglet-turing.properties with your database settings.

2. Start Turing ES (Linux/macOS):
     chmod +x bin/turing.sh
     ./bin/turing.sh

   Start Turing ES (Windows):
     bin\turing.bat

3. Open http://localhost:2700 in your browser.
   Default user: admin
   Set the admin password on the first access via the web interface.

Database Configuration
----------------------
Default: embedded H2 (development only).
For production, configure PostgreSQL or MariaDB in viglet-turing.properties.

Solr Setup
----------
Create a Solr collection using the provided templates:

  cd <SOLR_DIR>/bin
  ./solr create_collection -c turing -n turing \
    -d <INSTALL_DIR>/utils/solr/2026.1/turing/en

Documentation
-------------
  https://viglet.org/turing/
  https://github.com/openviglet/turing
