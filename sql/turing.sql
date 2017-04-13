SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";

--
-- Database: `viglet`
--

-- --------------------------------------------------------

--
-- Table structure for table `users`
--

CREATE TABLE `users` (
  `user_name` varchar(100) NOT NULL,
  `user_pass` varchar(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `user_roles`
--

CREATE TABLE `user_roles` (
  `user_name` varchar(15) NOT NULL,
  `role_name` varchar(15) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `vigEntities`
--

CREATE TABLE `vigEntities` (
`id` int(11) NOT NULL,
  `name` varchar(50) NOT NULL,
  `internal_name` varchar(50) NOT NULL,
  `description` varchar(255) NOT NULL,
  `local` int(11) NOT NULL,
  `collection_name` varchar(50) NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=161 DEFAULT CHARSET=latin1;

--
-- Dumping data for table `vigEntities`
--

INSERT INTO `vigEntities` (`id`, `name`, `internal_name`, `description`, `local`, `collection_name`) VALUES
(1, 'People', 'PN', 'Entidade de Pessoas', 1, 'persons'),
(2, 'Places', 'GL', 'Entidade de Lugares', 1, 'locations'),
(3, 'Fraud', 'FR', 'Entidade de Fraude', 1, 'frauds'),
(4, 'Organization', 'ON', 'Entidades de Organizações', 0, 'organizations'),
(5, 'Duration', 'DURATION', 'Entidade de Durações', 0, 'durations'),
(6, 'Ordinal', 'ORDINAL', 'Entidade Ordinal', 0, 'ordinals'),
(7, 'Misc', 'MISC', 'Entidade Misc', 0, 'miscs'),
(8, 'Date', 'DATE', 'Entidade Date', 0, 'dates'),
(9, 'Time', 'TIME', 'Entidade Time', 0, 'times'),
(10, 'Money', 'MONEY', 'Entidade Money', 0, 'moneys'),
(11, 'Percentage', 'PERCENTAGE', 'Entidade de Porcentagem', 0, 'percentages'),
(115, 'Simple Concepts', 'SimpleConcepts', 'Entidade de Conceitos Simples', 0, 'concept_simple'),
(116, 'Complex Concepts', 'ComplexConcepts', 'Entidade com Conceitos Complexos', 0, 'concept_complex'),
(117, 'IPTC', 'IPTC', 'Entidade de Categorias', 0, 'iptc'),
(118, 'AMBIENTE ECONÔMICO E SOCIAL', 'AMBIENTE_ECONÔMICO_E_SOCIAL', 'AMBIENTE ECONÔMICO E SOCIAL', 0, 'AMBIENTE_ECONÔMICO_E_SOCIAL'),
(119, 'TABELAS AUXILIARES', 'TABELAS_AUXILIARES', 'TABELAS AUXILIARES', 0, 'TABELAS_AUXILIARES'),
(122, 'APICULTURA', 'apicultura', '', 1, 'apicultura'),
(123, 'CONFECÇÃO', 'confeccao', '', 1, 'confeccao'),
(124, 'ARTESANATO', 'artesanato', '', 1, 'artesanato'),
(125, 'AQUICULTURA', 'aquicultura', '', 1, 'aquicultura'),
(126, 'COURO E CALÇADOS', 'couro_e_calcados', '', 1, 'couro_e_calcados'),
(127, 'CONSTRUÇÃO CIVIL', 'construcao_civil', '', 1, 'construcao_civil'),
(128, 'ALIMENTO', 'alimento', '', 1, 'alimento'),
(129, 'ROCHA ORNAMENTAL', 'rocha_ornamental', '', 1, 'rocha_ornamental'),
(131, 'GRÁFICA E EDITORA', 'grafica_e_editora', '', 1, 'grafica_e_editora'),
(132, 'TURISMO', 'turismo', '', 1, 'turismo'),
(133, 'TÊXTIL', 'textil', '', 1, 'textil'),
(134, 'CERÂMICA', 'ceramica', '', 1, 'ceramica'),
(135, 'BEBIDA', 'bebida', '', 1, 'bebida'),
(137, 'RECICLAGEM', 'reciclagem', '', 1, 'reciclagem'),
(138, 'PLÁSTICO E BORRACHA', 'plastico e borracha', '', 1, 'plastico e borracha'),
(139, 'CULTURA, ENTRETENIMENTO E LAZER', 'cultura entretenimento e lazer', '', 1, 'cultura entretenimento e lazer'),
(140, 'PETRÓLEO, GÁS E ENERGIA', 'petroleo_gas_e_energia', '', 1, 'petroleo_gas_e_energia'),
(141, 'METALURGIA, METAL MECÂNICA E AUTO PEÇAS', 'metalurgia_metal_mecanica_e_auto_pecas', '', 1, 'metalurgia_metal_mecanica_e_auto_pecas'),
(142, 'HIGIENE PESSOAL, PERFUMARIA E COSMÉTICO', 'higiene_pessoal_perfumaria_e_cosmetico', '', 1, 'higiene_pessoal_perfumaria_e_cosmetico'),
(143, 'ELETRO-ELETRÔNICA', 'eletroeletronica', '', 1, 'eletroeletronica'),
(144, 'QUÍMICA', 'quimica', '', 1, 'quimica'),
(145, 'GEMA, JÓIA E BIJUTERIA', 'gema_joia_e_bijuteria', '', 1, 'gema_joia_e_bijuteria'),
(146, 'EMPREENDEDORISMO', 'empreendedorismo', '', 1, 'empreendedorismo'),
(147, 'GESTÃO EMPRESARIAL', 'gestao_empresarial', '', 1, 'gestao_empresarial'),
(148, 'COMÉRCIO VAREJISTA E ATACADISTA', 'comercio_varejista_e_atacadista', '', 1, 'comercio_varejista_e_atacadista'),
(149, 'SAÚDE, BELEZA E MEDICINA', 'saude_beleza_e_medicina', '', 1, 'saude_beleza_e_medicina'),
(150, 'BIOTECNOLOGIA', 'biotecnologia', '', 1, 'biotecnologia'),
(151, 'PECUÁRIA', 'pecuaria', '', 1, 'pecuaria'),
(152, 'MADEIRA E MOBILIÁRIO', 'madeira_e_mobiliario', '', 1, 'madeira_e_mobiliario'),
(153, 'AMBIENTE NORMATIVO', 'ambiente_normativo', '', 1, 'ambiente_normativo'),
(154, 'TECNOLOGIA DA INFORMAÇÃO E COMUNICAÇÃO', 'tecnologia_da_informacao_e_comunicacao', '', 1, 'tecnologia_da_informacao_e_comunicacao'),
(155, 'VITIVINICULTURA', 'vitivinicultura', '', 1, 'vitivinicultura'),
(156, 'OUTROS', 'outros', '', 1, 'outros'),
(159, 'AGRICULTURA', 'agricultura', '', 0, 'agricultura');

-- --------------------------------------------------------

--
-- Table structure for table `vigNLPSolutions`
--

CREATE TABLE `vigNLPSolutions` (
  `id` int(11) NOT NULL,
  `title` varchar(100) NOT NULL,
  `plugin` varchar(255) NOT NULL,
  `description` varchar(255) NOT NULL,
  `website` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Dumping data for table `vigNLPSolutions`
--

INSERT INTO `vigNLPSolutions` (`id`, `title`, `plugin`, `description`, `website`) VALUES
(1, 'Stanford CoreNLP', 'com.viglet.turing.plugins.corenlp.CoreNLPConnector', '', 'http://stanfordnlp.github.io/CoreNLP'),
(2, 'OpenText OTCA', 'com.viglet.turing.plugins.otca.TmeConnector', '', 'http://opentext.com/what-we-do/products/discovery'),
(3, 'Apache OpenNLP', 'com.viglet.turing.plugins.opennlp.OpenNLPConnector', '', 'https://opennlp.apache.org'),
(4, 'SpaCy ', '', '', 'https://spacy.io'),
(5, 'NTLK', '', '', 'http://www.nltk.org'),
(6, 'Google SyntaxNet', '', '', 'https://www.tensorflow.org/versions/master/tutorials/syntaxnet/index.html'),
(7, 'MALLET', '', '', 'http://mallet.cs.umass.edu'),
(8, 'ClearNLP ', '', '', 'http://www.clearnlp.com'),
(9, 'VigletNLP', '', '', 'http://www.viglet.ai');

-- --------------------------------------------------------

--
-- Table structure for table `vigServices`
--

CREATE TABLE `vigServices` (
  `id` int(11) NOT NULL,
  `type` int(11) NOT NULL,
  `sub-type` int(11) NOT NULL,
  `title` varchar(100) NOT NULL,
  `description` varchar(100) NOT NULL,
  `host` varchar(255) NOT NULL,
  `port` int(11) NOT NULL,
  `language` varchar(5) NOT NULL,
  `enabled` int(11) NOT NULL,
  `selected` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Dumping data for table `vigServices`
--

INSERT INTO `vigServices` (`id`, `type`, `sub-type`, `title`, `description`, `host`, `port`, `language`, `enabled`, `selected`) VALUES
(0, 2, 9, 'embedded', 'Viglet Turing NLP Embedded', '', 0, 'pt_BR', 1, 0),
(1, 2, 2, 'otcadev01a', 'OTCA do Ambiente de Desenvolvimento do Sebrae', '172.16.32.135', 40000, 'pt_BR', 0, 0),
(2, 2, 1, 'corenlplx01a', 'CoreNLP do Ambiente de Produção da DBMaster', 'localhost', 32768, 'en_US', 1, 0),
(3, 3, 1, 'solrlnx02a', 'Solr', 'dbmaster.viglet.com', 80, '', 1, 1),
(4, 2, 3, 'opennlp01a', 'OpenNLP do Ambiente de Homologação do SebraePR', '', 0, 'en_US', 1, 1),
(5, 3, 1, 'solrlocal', 'Solr Local', 'localhost', 32769, '', 0, 0);

-- --------------------------------------------------------

--
-- Table structure for table `vigServicesNLPEntities`
--

CREATE TABLE `vigServicesNLPEntities` (
  `id` int(11) NOT NULL,
  `id_entity` int(11) NOT NULL,
  `id_service` int(11) NOT NULL,
  `name` varchar(255) NOT NULL,
  `enabled` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

--
-- Dumping data for table `vigServicesNLPEntities`
--

INSERT INTO `vigServicesNLPEntities` (`id`, `id_entity`, `id_service`, `name`, `enabled`) VALUES
(1, 1, 2, 'PERSON', 1),
(2, 2, 2, 'LOCATION', 1),
(3, 4, 2, 'ORGANIZATION', 1),
(4, 1, 1, 'PN', 1),
(5, 4, 1, 'ON', 1),
(6, 2, 1, 'GL', 1),
(7, 3, 3, 'FRAUD', 1),
(8, 5, 2, 'DURATION', 1),
(9, 8, 2, 'DATE', 1),
(10, 7, 2, 'MISC', 1),
(11, 6, 2, 'ORDINAL', 1),
(12, 9, 2, 'TIME', 1),
(13, 1, 4, '/models/opennlp/en/en-ner-person.bin', 0),
(14, 2, 4, '/models/opennlp/en/en-ner-location.bin', 0),
(15, 4, 4, '/models/opennlp/en/en-ner-organization.bin', 0),
(16, 8, 4, '/models/opennlp/en/en-ner-date.bin', 0),
(17, 10, 4, '/models/opennlp/en/en-ner-money.bin', 0),
(18, 9, 4, '/models/opennlp/en/en-ner-time.bin', 0),
(19, 11, 4, '/models/opennlp/en/en-ner-percentage.bin', 0),
(20, 115, 1, 'SimpleConcepts', 1),
(21, 116, 1, 'ComplexConcepts', 1),
(22, 117, 1, 'IPTC', 1),
(23, 118, 1, 'AMBIENTE ECONÔMICO E SOCIAL', 1),
(24, 119, 1, 'TABELAS AUXILIARES', 1),
(25, 122, 1, 'APICULTURA', 1),
(26, 123, 1, 'CONFECÇÃO', 1),
(27, 124, 1, 'ARTESANATO', 1),
(28, 125, 1, 'AQUICULTURA', 1),
(29, 126, 1, 'COURO E CALÇADOS', 1),
(30, 127, 1, 'CONSTRUÇÃO CIVIL', 1),
(31, 128, 1, 'ALIMENTO', 1),
(32, 129, 1, 'ROCHA ORNAMENTAL', 1),
(33, 159, 1, 'AGRICULTURA', 1),
(34, 131, 1, 'GRÁFICA E EDITORA', 1),
(35, 132, 1, 'TURISMO', 1),
(36, 133, 1, 'TÊXTIL', 1),
(37, 134, 1, 'CERÂMICA', 1),
(38, 135, 1, 'BEBIDA', 1),
(39, 137, 1, 'RECICLAGEM', 1),
(40, 138, 1, 'PLÁSTICO E BORRACHA', 1),
(41, 139, 1, 'CULTURA, ENTRETENIMENTO E LAZER', 1),
(42, 140, 1, 'PETRÓLEO, GÁS E ENERGIA', 1),
(43, 141, 1, 'METALURGIA, METAL MECÂNICA E AUTO PEÇAS', 1),
(44, 142, 1, 'HIGIENE PESSOAL, PERFUMARIA E COSMÉTICO', 1),
(45, 143, 1, 'ELETRO-ELETRÔNICA', 1),
(46, 144, 1, 'QUÍMICA', 1),
(47, 145, 1, 'GEMA, JÓIA E BIJUTERIA', 1),
(48, 146, 1, 'EMPREENDEDORISMO', 1),
(49, 147, 1, 'GESTÃO EMPRESARIAL', 1),
(50, 148, 1, 'COMÉRCIO VAREJISTA E ATACADISTA', 1),
(51, 149, 1, 'SAÚDE, BELEZA E MEDICINA', 1),
(52, 150, 1, 'BIOTECNOLOGIA', 1),
(53, 151, 1, 'PECUÁRIA', 1),
(54, 152, 1, 'MADEIRA E MOBILIÁRIO', 1),
(55, 153, 1, 'AMBIENTE NORMATIVO', 1),
(56, 154, 1, 'TECNOLOGIA DA INFORMAÇÃO E COMUNICAÇÃO', 1),
(57, 155, 1, 'VITIVINICULTURA', 1),
(58, 156, 1, 'OUTROS', 1);

-- --------------------------------------------------------

--
-- Table structure for table `vigTerm`
--

CREATE TABLE `vigTerm` (
`id` int(11) NOT NULL,
  `id_custom` varchar(255) NOT NULL,
  `entity_id` int(11) NOT NULL,
  `name` varchar(255) NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=147145 DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `vigTermAttribute`
--

CREATE TABLE `vigTermAttribute` (
`id` int(11) NOT NULL,
  `value` varchar(255) NOT NULL,
  `term_id` int(11) NOT NULL,
  `name` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `vigTermRelationFrom`
--

CREATE TABLE `vigTermRelationFrom` (
  `term_id` int(11) NOT NULL,
  `relation_type` int(11) NOT NULL,
`id` int(11) NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=4900 DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `vigTermRelationTo`
--

CREATE TABLE `vigTermRelationTo` (
  `term_id` int(11) NOT NULL,
  `relation_from_id` int(11) NOT NULL,
`id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `vigTermVariation`
--

CREATE TABLE `vigTermVariation` (
`id` int(11) NOT NULL,
  `name` varchar(255) NOT NULL,
  `name_lower` varchar(255) NOT NULL,
  `weight` double NOT NULL,
  `rule_prefix` varchar(255) DEFAULT NULL,
  `rule_prefix_required` int(11) DEFAULT NULL,
  `rule_suffix` varchar(255) DEFAULT NULL,
  `rule_suffix_required` int(11) DEFAULT NULL,
  `rule_case` int(11) NOT NULL,
  `rule_accent` int(11) NOT NULL,
  `term_id` int(11) NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=155789 DEFAULT CHARSET=latin1;

-- --------------------------------------------------------

--
-- Table structure for table `vigTermVariationLanguage`
--

CREATE TABLE `vigTermVariationLanguage` (
`id` int(11) NOT NULL,
  `language` varchar(10) NOT NULL,
  `variation_id` int(11) NOT NULL,
  `term_id` int(11) NOT NULL
) ENGINE=InnoDB AUTO_INCREMENT=3277 DEFAULT CHARSET=latin1;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `users`
--
ALTER TABLE `users`
 ADD PRIMARY KEY (`user_name`);

--
-- Indexes for table `user_roles`
--
ALTER TABLE `user_roles`
 ADD PRIMARY KEY (`user_name`,`role_name`);

--
-- Indexes for table `vigEntities`
--
ALTER TABLE `vigEntities`
 ADD PRIMARY KEY (`id`);

--
-- Indexes for table `vigNLPSolutions`
--
ALTER TABLE `vigNLPSolutions`
 ADD PRIMARY KEY (`id`);

--
-- Indexes for table `vigServices`
--
ALTER TABLE `vigServices`
 ADD PRIMARY KEY (`id`);

--
-- Indexes for table `vigServicesNLPEntities`
--
ALTER TABLE `vigServicesNLPEntities`
 ADD PRIMARY KEY (`id`), ADD KEY `id_entity` (`id_entity`), ADD KEY `id_service` (`id_service`);

--
-- Indexes for table `vigTerm`
--
ALTER TABLE `vigTerm`
 ADD PRIMARY KEY (`id`), ADD KEY `entity_id` (`entity_id`);

--
-- Indexes for table `vigTermAttribute`
--
ALTER TABLE `vigTermAttribute`
 ADD PRIMARY KEY (`id`), ADD KEY `term_id` (`term_id`);

--
-- Indexes for table `vigTermRelationFrom`
--
ALTER TABLE `vigTermRelationFrom`
 ADD PRIMARY KEY (`id`), ADD KEY `term_id` (`term_id`);

--
-- Indexes for table `vigTermRelationTo`
--
ALTER TABLE `vigTermRelationTo`
 ADD PRIMARY KEY (`id`), ADD KEY `term_id` (`term_id`), ADD KEY `relation_from_id` (`relation_from_id`);

--
-- Indexes for table `vigTermVariation`
--
ALTER TABLE `vigTermVariation`
 ADD PRIMARY KEY (`id`), ADD KEY `term_id` (`term_id`), ADD KEY `id` (`id`), ADD KEY `id_2` (`id`);

--
-- Indexes for table `vigTermVariationLanguage`
--
ALTER TABLE `vigTermVariationLanguage`
 ADD PRIMARY KEY (`id`), ADD KEY `variation_id` (`variation_id`), ADD KEY `term_id` (`term_id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `vigEntities`
--
ALTER TABLE `vigEntities`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT,AUTO_INCREMENT=161;
--
-- AUTO_INCREMENT for table `vigTerm`
--
ALTER TABLE `vigTerm`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT,AUTO_INCREMENT=147145;
--
-- AUTO_INCREMENT for table `vigTermAttribute`
--
ALTER TABLE `vigTermAttribute`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT for table `vigTermRelationFrom`
--
ALTER TABLE `vigTermRelationFrom`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT,AUTO_INCREMENT=4900;
--
-- AUTO_INCREMENT for table `vigTermRelationTo`
--
ALTER TABLE `vigTermRelationTo`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
--
-- AUTO_INCREMENT for table `vigTermVariation`
--
ALTER TABLE `vigTermVariation`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT,AUTO_INCREMENT=155789;
--
-- AUTO_INCREMENT for table `vigTermVariationLanguage`
--
ALTER TABLE `vigTermVariationLanguage`
MODIFY `id` int(11) NOT NULL AUTO_INCREMENT,AUTO_INCREMENT=3277;
--
-- Constraints for dumped tables
--

--
-- Constraints for table `vigServicesNLPEntities`
--
ALTER TABLE `vigServicesNLPEntities`
ADD CONSTRAINT `vigservicesnlpentities_ibfk_1` FOREIGN KEY (`id_entity`) REFERENCES `vigEntities` (`id`),
ADD CONSTRAINT `vigservicesnlpentities_ibfk_2` FOREIGN KEY (`id_service`) REFERENCES `vigServices` (`id`);

--
-- Constraints for table `vigTerm`
--
ALTER TABLE `vigTerm`
ADD CONSTRAINT `vigterm_ibfk_1` FOREIGN KEY (`entity_id`) REFERENCES `vigEntities` (`id`);

--
-- Constraints for table `vigTermAttribute`
--
ALTER TABLE `vigTermAttribute`
ADD CONSTRAINT `vigtermattribute_ibfk_1` FOREIGN KEY (`term_id`) REFERENCES `vigTerm` (`id`);

--
-- Constraints for table `vigTermRelationFrom`
--
ALTER TABLE `vigTermRelationFrom`
ADD CONSTRAINT `vigtermrelationfrom_ibfk_1` FOREIGN KEY (`term_id`) REFERENCES `vigTerm` (`id`);

--
-- Constraints for table `vigTermRelationTo`
--
ALTER TABLE `vigTermRelationTo`
ADD CONSTRAINT `vigtermrelationto_ibfk_2` FOREIGN KEY (`term_id`) REFERENCES `vigTerm` (`id`),
ADD CONSTRAINT `vigtermrelationto_ibfk_1` FOREIGN KEY (`relation_from_id`) REFERENCES `vigTermRelationFrom` (`id`);

--
-- Constraints for table `vigTermVariation`
--
ALTER TABLE `vigTermVariation`
ADD CONSTRAINT `vigtermvariation_ibfk_1` FOREIGN KEY (`term_id`) REFERENCES `vigTerm` (`id`);

--
-- Constraints for table `vigTermVariationLanguage`
--
ALTER TABLE `vigTermVariationLanguage`
ADD CONSTRAINT `vigtermvariationlanguage_ibfk_1` FOREIGN KEY (`variation_id`) REFERENCES `vigTermVariation` (`id`),
ADD CONSTRAINT `vigtermvariationlanguage_ibfk_2` FOREIGN KEY (`term_id`) REFERENCES `vigTerm` (`id`);
