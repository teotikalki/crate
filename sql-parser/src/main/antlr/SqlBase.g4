/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

grammar SqlBase;

//@header {
//    package io.crate.sql.parser;
//}
//
//@lexer::header {
//    package io.crate.sql.parser;
//}

//@members {
//    @Override
//    protected Object recoverFromMismatchedToken(IntStream input, int tokenType, BitSet follow)
//            throws RecognitionException
//    {
//        throw new MismatchedTokenException(tokenType, input);
//    }
//
//    @Override
//    public Object recoverFromMismatchedSet(IntStream input, RecognitionException e, BitSet follow)
//            throws RecognitionException
//    {
//        throw e;
//    }
//
//    @Override
//    public String getErrorMessage(RecognitionException e, String[] tokenNames)
//    {
//        if (e.token.getType() == BACKQUOTED_IDENT) {
//            return "backquoted identifiers are not supported; use double quotes to quote identifiers";
//        }
//        if (e.token.getType() == DIGIT_IDENT) {
//            return "identifiers must not start with a digit; surround the identifier with double quotes";
//        }
//        if (e.token.getType() == COLON_IDENT) {
//            return "identifiers must not contain a colon; use '@' instead of ':' for table links";
//        }
//        return super.getErrorMessage(e, tokenNames);
//    }
//}
//
//@lexer::members {
//    @Override
//    public void reportError(RecognitionException e)
//    {
//        throw new ParsingException(getErrorMessage(e, getTokenNames()), e);
//    }
//}
//
//@rulecatch {
//    catch (RecognitionException re) {
//        throw new ParsingException(getErrorMessage(re, getTokenNames()), re);
//    }
//}

singleStatement
    : statement EOF
    ;

singleExpression
    : expr EOF
    ;

statement
    : query                                                                                 #default
    | BEGIN                                                                                 #begin
    | EXPLAIN statement                                                                     #explain
    | RESET GLOBAL columnList                                                               #resetGlobal
    | OPTIMIZE TABLE tableWithPartitionList (WITH '(' genericProperties ')' )?              #optimize
    | REFRESH TABLE tableWithPartitionList                                                  #refreshTable
    | UPDATE aliasedRelation SET assignment ( ',' assignment )* whereClause?                   #update
    | DELETE FROM aliasedRelation whereClause?                                                 #delete
    | SHOW CREATE TABLE table                                                               #showCreateTable
    | SHOW TABLES ((FROM | IN) qname)?  (LIKE pattern=STRING | whereClause)?                #showTables
    | SHOW SCHEMAS  (LIKE pattern=STRING | whereClause)?                                    #showSchemas
    | SHOW COLUMNS (FROM | IN) qname ((FROM | IN) qname)?
        (LIKE pattern=STRING | whereClause)?  #showColumns
    | ALTER TABLE alterTableDefinition ADD COLUMN? addColumnDefinition                      #addColumn
    | ALTER TABLE alterTableDefinition (SET '(' genericProperties ')' | RESET identList)    #alterTableProperties
    | ALTER TABLE alterTableDefinition (SET '(' genericProperties ')' | RESET identList)    #alterBlobTableProperties
    | SET (SESSION | LOCAL)? setAssignment                                                  #set
    | SET GLOBAL (PERSISTENT | TRANSIENT)? setGlobalAssignment (',' setGlobalAssignment)*   #setGlobal
    | KILL ALL                                                                              #killAll
    | KILL parameterOrLiteral                                                               #kill
    | INSERT INTO table identList? insertSource onDuplicateKey?                             #insert
    | dropStmt                                                                              #drop
    | RESTORE restoreStmt                                                                   #restore
    | CREATE createStatement                                                                #create
//    | COPY copyStatement
//    | SET setStmt
    ;

dropStmt
	: DROP TABLE (IF EXISTS)? table                                                         #dropTable
	| DROP BLOB TABLE ( IF EXISTS )? table                                                  #dropBlobTable
	| DROP ALIAS qname                                                                      #dropAlias
	| DROP REPOSITORY repository                                                            #dropRepository
	| DROP SNAPSHOT qname                                                                   #dropSnapshot
	;

restoreStmt
    : SNAPSHOT qname
      allOrTableWithPartitionList
      (WITH '(' genericProperties ')' )?
    ;

query
    : queryExpr
    ;

queryExpr
    : withClause?
        (orderOrLimitOrOffsetQuerySpec | queryExprBody orderClause? limitClause? offsetClause?)
    ;

orderOrLimitOrOffsetQuerySpec
    : simpleQuery (orderClause limitClause? offsetClause? | limitClause offsetClause? | offsetClause)
    ;

queryExprBody
    : queryTerm
      ( UNION setQuant? queryTerm | EXCEPT setQuant? queryTerm)*
    ;

queryTerm
    : ( queryPrimary )
      ( INTERSECT setQuant? queryPrimary )*
    ;

queryPrimary
    : simpleQuery
    | tableSubquery
    | explicitTable
    ;

explicitTable
    : TABLE table
    ;

simpleQuery
    : selectClause
//      fromClause?
      whereClause?
      groupClause?
      havingClause?
    ;

restrictedSelectStmt
    : selectClause
//      fromClause
    ;

withClause
    : WITH r=RECURSIVE? withList
    ;

selectClause
    : SELECT selectExpr
    ;

//fromClause
//    : FROM tableRef (',' tableRef)*
//    ;

whereClause
    : WHERE expr
    ;

groupClause
    : GROUP BY expr (',' expr)*
    ;

havingClause
    : HAVING expr
    ;

orderClause
    : ORDER BY sortItem (',' sortItem)*
    ;

limitClause
    : LIMIT integer
    | LIMIT parameterExpr
    ;

offsetClause
    : OFFSET integer
    | OFFSET parameterExpr
    ;

withList
    : withQuery (',' withQuery)*
    ;

withQuery
    : ident aliasedColumns? AS? subquery
    ;

selectExpr
    : setQuant? selectList
    ;

setQuant
    : DISTINCT
    | ALL
    ;

selectList
    : selectSublist (',' selectSublist)*
    ;

selectSublist
    : expr (AS? ident)?
    | qname '.' '*'
    | '*'
    ;

//tableRef
//    : ( tableFactor )
//      ( CROSS JOIN tableFactor
//      | joinType JOIN tableFactor joinCriteria
//      | NATURAL joinType JOIN tableFactor
//      )*
//    ;

//tableFactor
//    : ( tablePrimary )
//      ( TABLESAMPLE sampleType '(' expression ')' stratifyOn?)?
//    ;

aliasedRelation
    : relation (AS? ident aliasedColumns?)?
    ;

relation
    : table
//    | ('(' tableRef ')')
    | tableSubquery
    ;

tableWithPartition
    : qname ( PARTITION '(' assignment ( ',' assignment )* ')' )?
    ;

table
    : qname
    | ident '(' parameterOrLiteral? (',' parameterOrLiteral)* ')'
    ;

repository
    : ident
    ;

tableOnly
    : ONLY qname
    ;

tableSubquery
    : '(' query ')'
    ;
//
//joinedTable
//    : '(' tableRef ')'
//    ;

joinType
    : INNER?
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    ;

joinCriteria
    : ON expr
    | USING '(' ident (',' ident)* ')'
    ;

aliasedColumns
    : '(' ident (',' ident)* ')'
    ;

expr
    : orExpression
    ;

orExpression
    : andExpression (OR andExpression)*
    ;

andExpression
    : notExpression (AND notExpression)*
    ;

notExpression
    : (NOT)* booleanTest
    ;

booleanTest
    : booleanPrimary
    ;

booleanPrimary
    : predicate
    | EXISTS subquery
    ;

predicate
//    : (MATCH) => matchPredicate
    : ((predicatePrimary )
      ( cmpOp quant=setCmpQuantifier '(' e=predicatePrimary ')'
//      | (LIKE setCmpQuantifier) => LIKE quant=setCmpQuantifier '(' e=predicatePrimary ')' (ESCAPE x=predicatePrimary)?
      | LIKE e=predicatePrimary (ESCAPE x=predicatePrimary)?
//      | (NOT LIKE setCmpQuantifier) => NOT LIKE quant=setCmpQuantifier '(' e=predicatePrimary ')' (ESCAPE x=predicatePrimary)?
      | NOT LIKE e=predicatePrimary (ESCAPE x=predicatePrimary)?
      | cmpOp e=predicatePrimary
      | IS DISTINCT FROM e=predicatePrimary
      | IS NOT DISTINCT FROM e=predicatePrimary
      | BETWEEN min=predicatePrimary AND max=predicatePrimary
      | NOT BETWEEN min=predicatePrimary AND max=predicatePrimary
      | IS NULL
      | IS NOT NULL
//      | IN inList
//      | NOT IN inList
      )*)
    ;

//matchPredicate
//    : MATCH '(' matchPredicateIdentList ',' s=exprPrimary ')' (USING matchMethod=ident ((WITH '(') => WITH '(' genericProperties ')' )?)? -> ^(MATCH matchPredicateIdentList $s $matchMethod? genericProperties?)
//    ;

//matchPredicateIdentList
//    : ('(' matchPredicateIdent) => '(' matchPredicateIdent (',' matchPredicateIdent)* ')' -> ^(MATCH_PREDICATE_IDENT_LIST matchPredicateIdent+)
//    | matchPredicateIdent  -> ^(MATCH_PREDICATE_IDENT_LIST matchPredicateIdent+)
//    ;
//
//matchPredicateIdent
//    : subscriptSafe parameterOrSimpleLiteral? -> ^(MATCH_PREDICATE_IDENT subscriptSafe parameterOrSimpleLiteral?)
//    ;

// TDODO primaryExpression!!!!!
predicatePrimary
    : (numericExpr )
//      ( '||' e=numericExpr -> ^(FUNCTION_CALL ^(QNAME IDENT["concat"]) $predicatePrimary $e) )*
    ;


numericExpr
    : numericTerm (('+' | '-') numericTerm)*
    ;
//
numericTerm
    : numericFactor (('*' | '/' | '%') numericFactor)*
    ;

numericFactor
    : subscript
    | '+' numericFactor
    | '-' numericFactor
    ;

subscript
    : value=exprPrimary ('[' index=numericExpr ']')*
    ;

subscriptSafe
    : value=qname ('[' index=numericExpr ']')*
    ;

exprPrimary
    : simpleExpr
    | caseExpression
    | '(' expr ')'
//    | subquery
    ;

simpleExpr
    : nullLiteral
    | dateLiteral
    | arrayLiteral
    | qnameOrFunction
    | specialFunction
    | number
    | parameterExpr
    | boolLiteral
    | stringLiteral
    ;

stringLiteral
    : STRING
    ;

nullLiteral
    : NULL
    ;

identExpr
    : parameterOrSimpleLiteral
    | ident
    ;

parameterOrLiteral
    : parameterOrSimpleLiteral
    | arrayLiteral
    ;

parameterOrSimpleLiteral
    : nullLiteral
    | numericLiteral
    | parameterExpr
    | boolLiteral
    | stringLiteral
    ;


qnameOrFunction
    : qname
//      ( ('(' '*' ')' over?                          -> ^(FUNCTION_CALL $qnameOrFunction over?))
//      | ('(' setQuant? expression? (',' expression)* ')' over?  -> ^(FUNCTION_CALL $qnameOrFunction over? setQuant? expression*))
//      )?
    ;

numericLiteral
    : '+'? number
    | '-' number
    ;

parameterExpr
    : '$' integer                                                                    #positionalParameter
    | '?'                                                                            #parameterPlaceholder
    ;

//inList
//    : ('(' expression) => ('(' expression (',' expression)* ')' -> ^(IN_LIST expression+))
//    : subquery
//    ;

sortItem
    : expr ordering nullOrdering?
    ;

ordering
    : ASC
    | DESC
    ;

nullOrdering
    : NULLS FIRST
    | NULLS LAST
    ;

cmpOp
    : EQ | NEQ | LT | LTE | GT | GTE | REGEX_MATCH | REGEX_NO_MATCH | REGEX_MATCH_CI | REGEX_NO_MATCH_CI
    ;

setCmpQuantifier
    : ANY | SOME | ALL
    ;

subquery
    : '(' query ')'
    ;

dateLiteral
    : DATE STRING
    | TIME STRING
    | TIMESTAMP STRING
    ;

nonSecond
    : YEAR | MONTH | DAY | HOUR | MINUTE
    ;

specialFunction
    : CURRENT_DATE
    | CURRENT_TIME ('(' integer ')')?
    | CURRENT_TIMESTAMP ('(' integer ')')?
    | CURRENT_SCHEMA ('(' ')')?
    | SUBSTRING '(' expr FROM expr (FOR expr)? ')'
    | EXTRACT '(' identExpr FROM expr ')'
    | CAST '(' expr AS dataType ')'
    | TRY_CAST '(' expr AS dataType ')'
    ;

caseExpression
    : CASE expr whenClause+ elseClause? END
    | CASE whenClause+ elseClause? END
    | IF '(' expr ',' expr (',' expr)? ')'
    ;

whenClause
    : WHEN expr THEN expr
    ;

elseClause
    : ELSE expr
    ;

likeOrWhere
    : LIKE pattern=STRING | whereClause
    ;

viewRefresh
    : REFRESH r=integer
    ;

forRemote
    : FOR qname
    ;

tableContentsSource
    : AS query
    ;

qname
    : ident ('.' ident)*
    ;

ident
    : IDENTIFIER                                                                     #unquotedIdentifier
    | quotedIdentifier                                                               #quotedIdentifierAlternative
    | nonReserved                                                                    #unquotedIdentifier
    | BACKQUOTED_IDENTIFIER                                                          #backQuotedIdentifier
    | DIGIT_IDENTIFIER                                                               #digitIdentifier
    ;

quotedIdentifier
    : QUOTED_IDENTIFIER
    ;

number
    : DECIMAL_VALUE                                                                  #decimalLiteral
    | INTEGER_VALUE                                                                  #integerLiteral
    ;

boolLiteral
    : TRUE
    | FALSE
    ;

jobId
    : parameterOrLiteral
    ;

integer
    : INTEGER_VALUE
    ;

arrayLiteral
    : ARRAY? '[' ( expr (',' expr)* )? ']'
    ;

object
    : '{' (objectKeyValue (',' objectKeyValue)* )? '}'
    ;

objectKeyValue
    : ident EQ parameterOrLiteral
    ;

onDuplicateKey
    : ON DUPLICATE KEY UPDATE assignment ( ',' assignment )*
    ;

insertSource
   : VALUES values=insertValues
   | '(' query ')'
   ;

identList
    : '(' ident ( ',' ident )* ')'
    ;

columnList
    : numericExpr ( ',' numericExpr )*
    ;

insertValues
    : valuesList ( ',' valuesList )*
    ;

valuesList
    : '(' expr (',' expr)* ')'
    ;

assignment
    : numericExpr EQ expr
    ;

// COPY STATEMENTS
//copyStatement
//    : tableWithPartition (
//        (FROM) => FROM expression ( WITH '(' genericProperties ')' )?
//        |
//        ( '(' columnList ')' )? whereClause? TO DIRECTORY? expression ( WITH '(' genericProperties ')' )?
//    )
//    ;
// END COPY STATEMENT

// CREATE STATEMENTS

createStatement
    : TABLE createTableStmt
    | BLOB TABLE createBlobTableStmt
    | ALIAS createAliasStmt
    | ANALYZER createAnalyzerStmt
//    | REPOSITORY createRepositoryStmt
    | SNAPSHOT createSnapshotStmt
    ;

createTableStmt
    : ( IF NOT EXISTS )? table
      tableElementList
      crateTableOption*
      (WITH '(' genericProperties ')' )?
    ;

createBlobTableStmt
    : table clusteredInto?
      (WITH '(' genericProperties ')' )?
    ;

createAliasStmt
    : qname forRemote
    ;

createAnalyzerStmt
    : ident extendsAnalyzer? analyzerElementList
    ;

createRepositoryStmt
    : repository
      TYPE ident
      (WITH '(' genericProperties ')' )?
    ;

createSnapshotStmt
    : qname
      allOrTableWithPartitionList
      (WITH '(' genericProperties ')' )?
    ;

alterTableDefinition
    : tableOnly
    | tableWithPartition
    ;

crateTableOption
    : clusteredBy
    | partitionedBy
    ;

tableElementList
    : '(' tableElement (',' tableElement)* ')'
    ;

tableElement
    :   columnDefinition
    |   indexDefinition
    |   primaryKeyConstraint
    ;

addColumnDefinition
    : addGeneratedColumnDefinition
    | subscriptSafe dataType columnConstDef*
    ;

columnDefinition
    : generatedColumnDefinition
    | ident dataType columnConstDef*
    ;

generatedColumnDefinition
    : ident GENERATED ALWAYS AS expr columnConstDef*
    | ident (dataType GENERATED ALWAYS)? AS expr columnConstDef*
    ;

addGeneratedColumnDefinition
    : subscriptSafe (dataType GENERATED ALWAYS)? AS expr columnConstDef*
//    : (subscriptSafe GENERATED ALWAYS AS) => subscriptSafe GENERATED ALWAYS AS expression columnConstDef*
    ;

dataType
    : STRING_TYPE
    | BOOLEAN
    | BYTE
    | SHORT
    | INT
    | INTEGER
    | LONG
    | FLOAT
    | DOUBLE
    | TIMESTAMP
    | IP
    | GEO_POINT
    | GEO_SHAPE
    | objectTypeDefinition
    | arrayTypeDefinition
    | setTypeDefinition
    ;

objectTypeDefinition
    : OBJECT ( '(' objectType ')' )? objectColumns?
    ;

arrayTypeDefinition
    : ARRAY '(' dataType ')'
    ;

setTypeDefinition
    : SET '(' dataType ')'
    ;

objectType
    : DYNAMIC
    | STRICT
    | IGNORED
    ;

objectColumns
    : AS '(' columnDefinition ( ',' columnDefinition )* ')'
    ;

columnConstDef
    : columnConst
    ;

columnConst
    : PRIMARY_KEY
    | NOT NULL
    | columnIndexConstraint
    ;

columnIndexConstraint
    : INDEX USING indexMethod=ident (WITH '(' genericProperties ')' )?
    | INDEX OFF
    ;

indexDefinition
    : INDEX ident USING indexMethod=ident '(' columnList ')' (WITH '(' genericProperties ')' )?
    ;

genericProperties
    :  genericProperty ( ',' genericProperty )*
    ;

genericProperty
    : ident EQ expr
    ;

primaryKeyConstraint
    : PRIMARY_KEY '(' columnList ')'
    ;

clusteredInto
    : CLUSTERED INTO parameterOrSimpleLiteral SHARDS
    ;

clusteredBy
    : CLUSTERED (BY '(' numericExpr ')' )? (INTO parameterOrSimpleLiteral SHARDS)?
    ;

partitionedBy
    : PARTITIONED BY '(' columnList ')'
    ;

extendsAnalyzer
    : EXTENDS ident
    ;

analyzerElementList
    : WITH? '(' analyzerElement ( ',' analyzerElement )* ')'
    ;

analyzerElement
    : tokenizer
    | tokenFilters
    | charFilters
    | genericProperty
    ;

tokenizer
    : TOKENIZER namedProperties
    ;

tokenFilters
    : TOKEN_FILTERS '(' namedProperties (',' namedProperties )* ')'
    ;

charFilters
    : CHAR_FILTERS '(' namedProperties (',' namedProperties )* ')'
    ;

namedProperties
    : ident (WITH '(' genericProperties ')' )?
    ;

tableWithPartitionList
    : tableWithPartition ( ',' tableWithPartition )*
    ;

allOrTableWithPartitionList
    : ALL
    | TABLE tableWithPartitionList
    ;

setGlobalAssignment
    : name=numericExpr (EQ | TO) value=expr
    ;

setAssignment
    : name=numericExpr (EQ | TO) value=setExprList
    ;

setExprList
    : DEFAULT
    | setExpr ( ',' setExpr )*
    ;

setExpr
    : stringLiteral
    | numericLiteral
    | boolLiteral
    | ident
    | on
    ;

on
    : ON
    ;

nonReserved
    : ALIAS | ANALYZER | BERNOULLI | BLOB | CATALOGS | CHAR_FILTERS | CLUSTERED
    | COLUMNS | COPY | CURRENT | DATE | DAY | DISTRIBUTED | DUPLICATE | DYNAMIC | EXPLAIN
    | EXTENDS | FOLLOWING | FORMAT | FULLTEXT | FUNCTIONS | GEO_POINT | GEO_SHAPE | GLOBAL
    | GRAPHVIZ | HOUR | IGNORED | KEY | KILL | LOGICAL | LOCAL | MATERIALIZED | MINUTE
    | MONTH | OFF | ONLY | OVER | OPTIMIZE | PARTITION | PARTITIONED | PARTITIONS | PLAIN
    | PRECEDING | RANGE | REFRESH | ROW | ROWS | SCHEMAS | SECOND | SESSION
    | SHARDS | SHOW | STRICT | SYSTEM | TABLES | TABLESAMPLE | TEXT | TIME
    | TIMESTAMP | TO | TOKENIZER | TOKEN_FILTERS | TYPE | VALUES | VIEW | YEAR
    | REPOSITORY | SNAPSHOT | RESTORE | GENERATED | ALWAYS | BEGIN
    | ISOLATION | TRANSACTION | LEVEL
    ;

SELECT: 'SELECT';
FROM: 'FROM';
TO: 'TO';
AS: 'AS';
ALL: 'ALL';
ANY: 'ANY';
SOME: 'SOME';
DIRECTORY: 'DIRECTORY';
DISTINCT: 'DISTINCT';
WHERE: 'WHERE';
GROUP: 'GROUP';
BY: 'BY';
ORDER: 'ORDER';
HAVING: 'HAVING';
LIMIT: 'LIMIT';
OFFSET: 'OFFSET';
OR: 'OR';
AND: 'AND';
IN: 'IN';
NOT: 'NOT';
EXISTS: 'EXISTS';
BETWEEN: 'BETWEEN';
LIKE: 'LIKE';
IS: 'IS';
NULL: 'NULL';
TRUE: 'TRUE';
FALSE: 'FALSE';
NULLS: 'NULLS';
FIRST: 'FIRST';
LAST: 'LAST';
ESCAPE: 'ESCAPE';
ASC: 'ASC';
DESC: 'DESC';
SUBSTRING: 'SUBSTRING';
FOR: 'FOR';
DATE: 'DATE';
TIME: 'TIME';
YEAR: 'YEAR';
MONTH: 'MONTH';
DAY: 'DAY';
HOUR: 'HOUR';
MINUTE: 'MINUTE';
SECOND: 'SECOND';
CURRENT_DATE: 'CURRENT_DATE';
CURRENT_TIME: 'CURRENT_TIME';
CURRENT_TIMESTAMP: 'CURRENT_TIMESTAMP';
CURRENT_SCHEMA: 'CURRENT_SCHEMA';
EXTRACT: 'EXTRACT';
CASE: 'CASE';
WHEN: 'WHEN';
THEN: 'THEN';
ELSE: 'ELSE';
END: 'END';
IF: 'IF';
JOIN: 'JOIN';
CROSS: 'CROSS';
OUTER: 'OUTER';
INNER: 'INNER';
LEFT: 'LEFT';
RIGHT: 'RIGHT';
FULL: 'FULL';
NATURAL: 'NATURAL';
USING: 'USING';
ON: 'ON';
OVER: 'OVER';
PARTITION: 'PARTITION';
RANGE: 'RANGE';
ROWS: 'ROWS';
UNBOUNDED: 'UNBOUNDED';
PRECEDING: 'PRECEDING';
FOLLOWING: 'FOLLOWING';
CURRENT: 'CURRENT';
ROW: 'ROW';
WITH: 'WITH';
RECURSIVE: 'RECURSIVE';
CREATE: 'CREATE';
BLOB: 'BLOB';
TABLE: 'TABLE';
REPOSITORY: 'REPOSITORY';
SNAPSHOT: 'SNAPSHOT';
ALTER: 'ALTER';
KILL: 'KILL';
ONLY: 'ONLY';

ADD: 'ADD';
COLUMN: 'COLUMN';

BOOLEAN: 'BOOLEAN';
BYTE: 'BYTE';
SHORT: 'SHORT';
INTEGER: 'INTEGER';
INT: 'INT';
LONG: 'LONG';
FLOAT: 'FLOAT';
DOUBLE: 'DOUBLE';
TIMESTAMP: 'TIMESTAMP';
IP: 'IP';
OBJECT: 'OBJECT';
STRING_TYPE: 'STRING';
GEO_POINT: 'GEO_POINT';
GEO_SHAPE: 'GEO_SHAPE';
GLOBAL : 'GLOBAL';
SESSION : 'SESSION';
LOCAL : 'LOCAL';
BEGIN: 'BEGIN';

CONSTRAINT: 'CONSTRAINT';
DESCRIBE: 'DESCRIBE';
EXPLAIN: 'EXPLAIN';
FORMAT: 'FORMAT';
TYPE: 'TYPE';
TEXT: 'TEXT';
GRAPHVIZ: 'GRAPHVIZ';
LOGICAL: 'LOGICAL';
DISTRIBUTED: 'DISTRIBUTED';
CAST: 'CAST';
TRY_CAST: 'TRY_CAST';
SHOW: 'SHOW';
TRANSACTION: 'TRANSACTION';
ISOLATION: 'ISOLATION';
LEVEL: 'LEVEL';
TABLES: 'TABLES';
SCHEMAS: 'SCHEMAS';
CATALOGS: 'CATALOGS';
COLUMNS: 'COLUMNS';
PARTITIONS: 'PARTITIONS';
FUNCTIONS: 'FUNCTIONS';
MATERIALIZED: 'MATERIALIZED';
VIEW: 'VIEW';
OPTIMIZE: 'OPTIMIZE';
REFRESH: 'REFRESH';
RESTORE: 'RESTORE';
DROP: 'DROP';
ALIAS: 'ALIAS';
UNION: 'UNION';
EXCEPT: 'EXCEPT';
INTERSECT: 'INTERSECT';
SYSTEM: 'SYSTEM';
BERNOULLI: 'BERNOULLI';
TABLESAMPLE: 'TABLESAMPLE';
STRATIFY: 'STRATIFY';
INSERT: 'INSERT';
INTO: 'INTO';
VALUES: 'VALUES';
DELETE: 'DELETE';
UPDATE: 'UPDATE';
KEY: 'KEY';
DUPLICATE: 'DUPLICATE';
SET: 'SET';
RESET: 'RESET';
DEFAULT: 'DEFAULT';
COPY: 'COPY';
CLUSTERED: 'CLUSTERED';
SHARDS: 'SHARDS';
PRIMARY_KEY: 'PRIMARY KEY';
OFF: 'OFF';
FULLTEXT: 'FULLTEXT';
PLAIN: 'PLAIN';
INDEX: 'INDEX';

DYNAMIC: 'DYNAMIC';
STRICT: 'STRICT';
IGNORED: 'IGNORED';

ARRAY: 'ARRAY';

ANALYZER: 'ANALYZER';
EXTENDS: 'EXTENDS';
TOKENIZER: 'TOKENIZER';
TOKEN_FILTERS: 'TOKEN_FILTERS';
CHAR_FILTERS: 'CHAR_FILTERS';

PARTITIONED: 'PARTITIONED';

TRANSIENT: 'TRANSIENT';
PERSISTENT: 'PERSISTENT';

MATCH: 'MATCH';

GENERATED: 'GENERATED';
ALWAYS: 'ALWAYS';

EQ  : '=';
NEQ : '<>' | '!=';
LT  : '<';
LTE : '<=';
GT  : '>';
GTE : '>=';
REGEX_MATCH: '~';
REGEX_NO_MATCH: '!~';
REGEX_MATCH_CI: '~*';
REGEX_NO_MATCH_CI: '!~*';

PLUS: '+';
MINUS: '-';
ASTERISK: '*';
SLASH: '/';
PERCENT: '%';
CONCAT: '||';

STRING
    : '\'' ( ~'\'' | '\'\'' )* '\''
    ;

INTEGER_VALUE
    : DIGIT+
    ;

DECIMAL_VALUE
    : DIGIT+ '.' DIGIT*
    | '.' DIGIT+
    | DIGIT+ ('.' DIGIT*)? EXPONENT
    | '.' DIGIT+ EXPONENT
    ;

IDENTIFIER
    : (LETTER | '_') (LETTER | DIGIT | '_' | '@' | ':')*
    ;

DIGIT_IDENTIFIER
    : DIGIT (LETTER | DIGIT | '_' | '@' | ':')+
    ;

QUOTED_IDENTIFIER
    : '"' ( ~'"' | '""' )* '"'
    ;

BACKQUOTED_IDENTIFIER
    : '`' ( ~'`' | '``' )* '`'
    ;

COLON_IDENT
    : (LETTER | DIGIT | '_' )+ ':' (LETTER | DIGIT | '_' )+
    ;

fragment EXPONENT
    : 'E' [+-]? DIGIT+
    ;

fragment DIGIT
    : '0'..'9'
    ;

fragment LETTER
    : 'A'..'Z'
    ;

COMMENT
    : '--' ~[\r\n]* '\r'? '\n'? -> channel(HIDDEN)
    ;

WS
    : [ \r\n\t]+ -> channel(HIDDEN)
    ;

UNRECOGNIZED
    : .
    ;
