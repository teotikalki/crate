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
//        if (e.token.getType() == COLON_IDENT) {
//            return "identifiers must not contain a colon; use '@' instead of ':' for table links";
//        }
//        return super.getErrorMessage(e, tokenNames);
//    }
//
//        @Override
//        public void reportError(RecognitionException e)
//        {
//            throw new ParsingException(getErrorMessage(e, getTokenNames()), e);
//        }
//}

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
    | OPTIMIZE TABLE tableWithPartitionList props=withGenericProps?                         #optimize
    | REFRESH TABLE tableWithPartitionList                                                  #refreshTable
    | UPDATE aliasedRelation SET assignment ( ',' assignment )* where?                      #update
    | DELETE FROM aliasedRelation where?                                                    #delete
    | SHOW CREATE TABLE table                                                               #showCreateTable
    | SHOW TABLES ((FROM | IN) qname)? (LIKE pattern=stringLiteral | where)?                #showTables
    | SHOW SCHEMAS (LIKE pattern=stringLiteral | where)?                                    #showSchemas
    | SHOW COLUMNS (FROM | IN) tableName=qname ((FROM | IN) schema=qname)?
        (LIKE pattern=stringLiteral | where)?                                               #showColumns
    | ALTER TABLE alterTableDefinition ADD COLUMN? addColumnDefinition                      #addColumn
    | ALTER TABLE alterTableDefinition
        (SET '(' genericProperties ')' | RESET ('(' ident (',' ident)* ')')?)               #alterTableProperties
    | ALTER BLOB TABLE alterTableDefinition
        (SET '(' genericProperties ')' | RESET ('(' ident (',' ident)* ')')?)               #alterBlobTableProperties
    | SET (SESSION | LOCAL)? setAssignment                                                  #set
    | SET GLOBAL (PERSISTENT | TRANSIENT)? setGlobalAssignment (',' setGlobalAssignment)*   #setGlobal
    | KILL ALL                                                                              #killAll
    | KILL jobId                                                                            #kill
    | INSERT INTO table ('(' ident (',' ident)* ')')? insertSource
        (ON DUPLICATE KEY UPDATE assignment ( ',' assignment )*)?                           #insert
    | RESTORE SNAPSHOT qname allOrTableWithPartitionList props=withGenericProps?            #restore
//    | COPY copyStatement
    | dropStmt                                                                              #drop
    | createStmt                                                                            #create
    ;

dropStmt
	: DROP BLOB TABLE (IF EXISTS)? table                                                    #dropBlobTable
	| DROP TABLE (IF EXISTS)? table                                                         #dropTable
	| DROP ALIAS name=qname                                                                 #dropAlias
	| DROP REPOSITORY name=ident                                                            #dropRepository
	| DROP SNAPSHOT name=qname                                                              #dropSnapshot
	;

query
    :  with? queryNoWith
    ;

with
    : WITH RECURSIVE? namedQuery (',' namedQuery)*
    ;

queryNoWith:
      queryTerm
      (ORDER BY sortItem (',' sortItem)*)?
      (LIMIT limit=paramOrInteger)?
      (OFFSET offset=paramOrInteger)?
    ;

paramOrInteger
    : parameterExpr
    | integerLiteral
    ;

queryTerm
    : queryPrimary                                                                   #queryTermDefault
    | left=queryTerm operator=INTERSECT setQuant? right=queryTerm                    #setOperation
    | left=queryTerm operator=(UNION | EXCEPT) setQuant? right=queryTerm             #setOperation
    ;

setQuant
    : DISTINCT
    | ALL
    ;

queryPrimary
    : querySpecification                                                             #queryPrimaryDefault
    | TABLE qname                                                                    #explicitTable
    | VALUES expr (',' expr)*                                                        #inlineTable
    | '(' queryNoWith  ')'                                                           #subquery
    ;

sortItem
    : expr ordering=(ASC | DESC)? (NULLS nullOrdering=(FIRST | LAST))?
    ;

querySpecification
    : SELECT setQuant? selectItem (',' selectItem)*
      (FROM relation (',' relation)*)?
      (where)?
      (GROUP BY expr (',' expr)*)?
      (HAVING having=booleanExpression)?
    ;

namedQuery
    : name=ident (aliasedColumns)? AS '(' query ')'
    ;

selectItem
    : expr (AS? ident)?                                                              #selectSingle
    | qname '.' ASTERISK                                                             #selectAll
    | ASTERISK                                                                       #selectAll
    ;

where
    : WHERE condition=booleanExpression
    ;

relation
    : left=relation
      ( CROSS JOIN right=sampledRelation
      | joinType JOIN rightRelation=relation joinCriteria
      | NATURAL joinType JOIN right=sampledRelation
      )                                                                              #joinRelation
    | sampledRelation                                                                #relationDefault
    ;

joinType
    : INNER?
    | LEFT OUTER?
    | RIGHT OUTER?
    | FULL OUTER?
    ;

joinCriteria
    : ON booleanExpression
    | USING '(' ident (',' ident)* ')'
    ;

sampledRelation
    : aliasedRelation (TABLESAMPLE sampleType '(' percentage=expr ')' stratifyOn?)?
    ;

stratifyOn
    : STRATIFY ON '(' expr (',' expr)* ')'
    ;

sampleType
    : BERNOULLI
    | SYSTEM
    ;

aliasedRelation
    : relationPrimary (AS? ident aliasedColumns?)?
    ;

relationPrimary
    : table                                                                          #tableName
    | '(' query ')'                                                                  #subqueryRelation
    | '(' relation ')'                                                               #parenthesizedRelation
    ;

tableWithPartition
    : qname ( PARTITION '(' assignment ( ',' assignment )* ')' )?
    ;

table
    : qname
    | ident '(' parameterOrLiteral? (',' parameterOrLiteral)* ')'
    ;

tableOnly
    : ONLY qname
    ;

aliasedColumns
    : '(' ident (',' ident)* ')'
    ;

expr
    : booleanExpression
    ;

booleanExpression
    : predicated                                                                     #booleanDefault
    | NOT booleanExpression                                                          #logicalNot
    | left=booleanExpression operator=AND right=booleanExpression                    #logicalBinary
    | left=booleanExpression operator=OR right=booleanExpression                     #logicalBinary
    ;

predicated
    : valueExpression predicate[$valueExpression.ctx]?
    ;

predicate[ParserRuleContext value]
    : cmpOp right=valueExpression                                                    #comparison
    | cmpOp setCmpQuantifier '(' query ')'                                           #quantifiedComparison
    | NOT? BETWEEN lower=valueExpression AND upper=valueExpression                   #between
    | NOT? IN '(' expr (',' expr)* ')'                                               #inList
    | NOT? IN '(' query ')'                                                          #inSubquery
    | NOT? LIKE pattern=valueExpression (ESCAPE escape=valueExpression)?             #like
    | IS NOT? NULL                                                                   #nullPredicate
    | IS NOT? DISTINCT FROM right=valueExpression                                    #distinctFrom
    ;

valueExpression
    : numericExpr                                                                       #valueExpressionDefault
    | operator=(MINUS | PLUS) valueExpression                                           #arithmeticUnary
    | left=valueExpression operator=(ASTERISK | SLASH | PERCENT) right=valueExpression  #arithmeticBinary
    | left=valueExpression operator=(PLUS | MINUS) right=valueExpression                #arithmeticBinary
    | left=valueExpression CONCAT right=valueExpression                                 #concatenation
    ;

numericExpr
    : parameterOrLiteral                                                             #defaultParamOrLiteral
    | qname '(' ASTERISK ')' over?                                                   #functionCall
    | ident                                                                          #columnReference
    | qname '(' (setQuant? expr (',' expr)*)? ')' over?                              #functionCall
    | '(' query ')'                                                                  #subqueryExpression
    // This is an extension to ANSI SQL, which considers EXISTS to be a <boolean expression>
    | EXISTS '(' query ')'                                                           #exists
    | value=numericExpr '[' index=valueExpression ']'                                #subscript
    | ident ('.' ident)*                                                             #dereference
    | name=CURRENT_DATE                                                              #specialDateTimeFunction
    | name=CURRENT_TIME ('(' precision=integerLiteral')')?                           #specialDateTimeFunction
    | name=CURRENT_TIMESTAMP ('(' precision=integerLiteral')')?                      #specialDateTimeFunction
    | CURRENT_SCHEMA ('(' ')')?                                                      #currentSchema
    | SUBSTRING '(' expr FROM expr (FOR expr)? ')'                                   #substring
    | EXTRACT '(' identExpr FROM expr ')'                                            #extract
    | CAST '(' expr AS dataType ')'                                                  #cast
    | TRY_CAST '(' expr AS dataType ')'                                              #cast
    | CASE valueExpression whenClause+ (ELSE elseExpression=expr)? END               #simpleCase
    | CASE whenClause+ (ELSE elseExpression=expr)? END                               #searchedCase
    | IF '(' expr ',' expr (',' expr)? ')'                                           #ifCase
    ;

identExpr
    : parameterOrSimpleLiteral
    | ident
    ;

parameterOrLiteral
    : parameterOrSimpleLiteral
    | dateLiteral
    | arrayLiteral
    | objectLiteral
    ;

parameterOrSimpleLiteral
    : nullLiteral
    | stringLiteral
    | numericLiteral
    | booleanLiteral
    | parameterExpr
    ;

parameterExpr
    : '$' integerLiteral                                                             #positionalParameter
    | '?'                                                                            #parameterPlaceholder
    ;

nullLiteral
    : NULL
    ;

stringLiteral
    : STRING
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
//predicatePrimary
//    : (numericExpr )
//      ( '||' e=numericExpr -> ^(FUNCTION_CALL ^(QNAME IDENT["concat"]) $predicatePrimary $e) )*
//    ;

subscriptSafe
    : qname ('[' numericExpr ']')*
    ;

// not used in crate
over
    : OVER '('
        (PARTITION BY partition+=expr (',' partition+=expr)*)?
        (ORDER BY sortItem (',' sortItem)*)?
        windowFrame?
      ')'
    ;

windowFrame
    : frameType=RANGE start=frameBound
    | frameType=ROWS start=frameBound
    | frameType=RANGE BETWEEN start=frameBound AND end=frameBound
    | frameType=ROWS BETWEEN start=frameBound AND end=frameBound
    ;

frameBound
    : UNBOUNDED boundType=PRECEDING                                                  #unboundedFrame
    | UNBOUNDED boundType=FOLLOWING                                                  #unboundedFrame
    | CURRENT ROW                                                                    #currentRowBound
    | expr boundType=(PRECEDING | FOLLOWING)                                         #boundedFrame
    ;

cmpOp
    : EQ | NEQ | LT | LTE | GT | GTE | REGEX_MATCH | REGEX_NO_MATCH | REGEX_MATCH_CI | REGEX_NO_MATCH_CI
    ;

setCmpQuantifier
    : ANY | SOME | ALL
    ;

dateLiteral
    : DATE STRING
    | TIME STRING
    | TIMESTAMP STRING
    ;

whenClause
    : WHEN condition=expr THEN result=expr
    ;

viewRefresh
    : REFRESH r=integerLiteral
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

numericLiteral
    : decimalLiteral
    | integerLiteral
    ;

booleanLiteral
    : TRUE
    | FALSE
    ;

jobId
    : parameterExpr
    | stringLiteral
    ;

decimalLiteral
    : DECIMAL_VALUE
    ;

integerLiteral
    : INTEGER_VALUE
    ;

arrayLiteral
    : ARRAY? '[' (expr (',' expr)*)? ']'
    ;

objectLiteral
    : '{' (objectKeyValue (',' objectKeyValue)* )? '}'
    ;

objectKeyValue
    : key=ident EQ value=expr
    ;

insertSource
   : VALUES  valuesList (',' valuesList)*
   | query
   | '(' query ')'
   ;

valuesList
    : '(' expr (',' expr)* ')'
    ;

columnList
    : numericExpr (',' numericExpr)*
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

createStmt
    : CREATE TABLE ( IF NOT EXISTS )? table
        '(' tableElement (',' tableElement)* ')'
         crateTableOption* props=withGenericProps?                                   #createTable
    | CREATE BLOB TABLE table numShards=clusteredInto? props=withGenericProps?       #createBlobTable
    | CREATE REPOSITORY name=ident TYPE type=ident props=withGenericProps?           #createRepository
    | CREATE SNAPSHOT qname allOrTableWithPartitionList props=withGenericProps?      #createSnapshot
//    | CREATE ANALYZER ident extendsAnalyzer? analyzerElementList                            #createAnalyzer
    ;

alterTableDefinition
    : tableOnly
    | tableWithPartition
    ;

crateTableOption
    : clusteredBy
    | partitionedBy
    ;

tableElement
    : columnDefinition                                                               #columndDef
    | PRIMARY_KEY '(' columnList ')'                                                 #primaryKeyConstraint
    | INDEX name=ident USING method=ident '(' columnList ')' props=withGenericProps? #indexDefinition
    ;

columnDefinition
    : generatedColumnDefinition
    | ident dataType columnConstraint*
    ;

generatedColumnDefinition
    : ident GENERATED ALWAYS AS expr columnConstraint*
    | ident (dataType GENERATED ALWAYS)? AS expr columnConstraint*
    ;

addColumnDefinition
    : addGeneratedColumnDefinition
    | subscriptSafe dataType columnConstraint*
    ;

addGeneratedColumnDefinition
    : subscriptSafe (dataType GENERATED ALWAYS)? AS expr columnConstraint*
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

columnConstraint
    : PRIMARY_KEY                                                                    #columnConstraintPrimaryKey
    | NOT NULL                                                                       #columnConstraintNotNull
    | INDEX USING method=ident props=withGenericProps?                               #columnIndexConstraint
    | INDEX OFF                                                                      #columnIndexOff
    ;

withGenericProps
    : WITH '(' genericProperties ')'                                                 #withGenericProperties
    ;

genericProperties
    : genericProperty (',' genericProperty)*
    ;

genericProperty
    : ident EQ expr
    ;

clusteredInto
    : CLUSTERED INTO numShards=parameterOrSimpleLiteral SHARDS
    ;

clusteredBy
    : CLUSTERED (BY '(' routing=numericExpr ')')? (INTO numShards=parameterOrSimpleLiteral SHARDS)?
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
    : ident props=withGenericProps?
    ;

tableWithPartitionList
    : tableWithPartition (',' tableWithPartition)*
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
    | setExpr (',' setExpr)*
    ;

setExpr
    : stringLiteral
    | booleanLiteral
    | numericLiteral
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

READ: 'READ';

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
    : [0-9]
    ;

fragment LETTER
    : [A-Za-z]
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
