grammar JmesPath;

//expression
//    : expression DOT filterExp //subExpression
//    | expression arrayExpression // indexExpression
//    | arrayExpression // indexExpression
//    | flattenArrayExpression
//    | expression comparator expression // comparatorExpression
//    | expression OR expression // orExpression
//    | expression AND expression // andExpression
//    | propertySelectionExpression
//    | NOT expression // notExpression
//    | LPAREN expression RPAREN // parenExpression
//    | multiSelectList
//    | multiSelectHash
//    | literal
//    | functionExpression
//    | expression PIPE expression // pipeExpression
//    | rawString
//    | currentNode
//    | (PLUS | MINUS) expression // arithmeticExpression
//    | STAR
//    ;
//
//filterExp
//    : propertySelectionExpression | multiSelectList | multiSelectHash | functionExpression | STAR
//    ;
//
//propertySelectionExpression
//    : propertyNameExpression
//    ;


//grammar JmesPath;

//jmesPathExpression : expression EOF ;

expression
  : expression '.' chainedExpression # chainExpression
  | expression arrayExpression # bracketedExpression
  | arrayExpression # bracketExpression
  | '!' expression # notExpression
  | expression COMPARATOR expression # comparisonExpression
  | expression '&&' expression # andExpression
  | expression '||' expression # orExpression
  | propertySelectionExpression # identifierExpression
  | '(' expression ')' # parenExpression
  | wildcard # wildcardExpression
  | multiSelectList # multiSelectListExpression
  | multiSelectHash # multiSelectHashExpression
  | literal # literalExpression
  | functionExpression # functionCallExpression
  | expression '|' expression # pipeExpression
  | RAW_STRING # rawStringExpression
  | currentNode # currentNodeExpression
  ;


arrayExpression
    : '[' SIGNED_INT ']' #arrayIndexExpression
    | ']' from=SIGNED_INT? ':' to=SIGNED_INT? (':' step=SIGNED_INT?)? ']' #arraySliceExpression
    | '[*]' #arrayStarExpression
    | '[?'  expression ']' #arrayFilterExpression
    | '[]' #flattenArrayExpression
    ;

chainedExpression
  : propertySelectionExpression
  | multiSelectList
  | multiSelectHash
  | functionExpression
  | wildcard
  ;

wildcard : '*' ;

multiSelectList : '[' expression (',' expression)* ']' ;

multiSelectHash : '{' keyValueExpression (',' keyValueExpression)* '}' ;

keyValueExpression : propertySelectionExpression ':' expression ;

COMPARATOR
  : '<'
  | '<='
  | '=='
  | '>='
  | '>'
  | '!='
  ;

functionExpression
  : NAME '(' functionArg (',' functionArg)* ')'
  | NAME '(' ')'
  ;

functionArg
  : expression
  | expressionType
  ;

currentNode : '@' ;

expressionType : '&' expression ;

RAW_STRING : '\'' (RAW_ESC | ~['\\])* '\'' ;

fragment RAW_ESC : '\\' . ;

literal : '`' jsonValue '`' ;

propertySelectionExpression
  : NAME
  | STRING
  | JSON_CONSTANT
  ;

JSON_CONSTANT
  : 'true'
  | 'false'
  | 'null'
  ;

NAME : [a-zA-Z_] [a-zA-Z0-9_]* ;

jsonObject
  : '{' jsonObjectPair (',' jsonObjectPair)* '}'
  | '{' '}'
  ;

jsonObjectPair
  : STRING ':' jsonValue
  ;

jsonArray
  : '[' jsonValue (',' jsonValue)* ']'
  | '[' ']'
  ;

jsonValue
  : STRING # jsonStringValue
  | (REAL_OR_EXPONENT_NUMBER | SIGNED_INT) # jsonNumberValue
  | jsonObject # jsonObjectValue
  | jsonArray # jsonArrayValue
  | JSON_CONSTANT # jsonConstantValue
  ;

STRING
  : '"' (ESC | ~ ["\\])* '"'
  ;

fragment ESC
  : '\\' (["\\/bfnrt`] | UNICODE)
  ;

fragment UNICODE
  : 'u' HEX HEX HEX HEX
  ;

fragment HEX
  : [0-9a-fA-F]
  ;

REAL_OR_EXPONENT_NUMBER
  : '-'? INT '.' [0-9] + EXP?
  | '-'? INT EXP
  ;

SIGNED_INT : '-'? INT ;

fragment INT
  : '0'
  | [1-9] [0-9]*
  ;

fragment EXP
  : [Ee] [+\-]? INT
  ;

WS
  : [ \t\n\r] + -> skip
  ;
