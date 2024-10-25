DROP TABLE WEMSYS.TUR_SPOTLIGHT_CONTENT;
CREATE TABLE WEMSYS.TUR_SPOTLIGHT_CONTENT (
    ID VARCHAR2(40),
    TITLE VARCHAR2(100),
    CONTENT VARCHAR2(2000),
    LINK VARCHAR2(255),
    SPOTLIGHT_ID VARCHAR2(40) NOT NULL,
    SEQUENCE_NUM INTEGER,
    POSITION INTEGER,
    PRIMARY KEY(ID)
);