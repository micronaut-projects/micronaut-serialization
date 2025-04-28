grammar JmesPath;

expression
    : expression DOT filterExp //subExpression
    | expression arrayExpression // indexExpression
    | arrayExpression // indexExpression
    | flattenArrayExpression
    | expression comparator expression // comparatorExpression
    | expression OR expression // orExpression
    | expression AND expression // andExpression
    | propertySelectionExpression
    | NOT expression // notExpression
    | LPAREN expression RPAREN // parenExpression
    | multiSelectList
    | multiSelectHash
    | literal
    | functionExpression
    | expression PIPE expression // pipeExpression
    | rawString
    | currentNode
    | (PLUS | MINUS) expression // arithmeticExpression
    | STAR
    ;

filterExp
    : propertySelectionExpression | multiSelectList | multiSelectHash | functionExpression | STAR
    ;

propertySelectionExpression
    : propertyNameExpression
    ;

flattenArrayExpression
    : LBRACK RBRACK
    ;

arrayExpression
    : LBRACK number RBRACK #arrayIndexExpression
    | LBRACK number? COLON number? (COLON number?)? RBRACK #arraySliceExpression
    | LBRACK STAR RBRACK #arrayStarExpression
    | LBRACK QUESTION expression RBRACK #arrayFilterExpression
    ;

comparator
    : LT | LTE | EQ | GTE | GT | NEQ
    ;

multiSelectList
    : LBRACK expression (COMMA expression)* RBRACK
    ;

multiSelectHash
    : LBRACE keyValueExpression (COMMA keyValueExpression)* RBRACE
    ;

keyValueExpression
    : propertyNameExpression COLON expression
    ;

functionExpression
    : unquotedString ( noArgs | oneOrMoreArgs )
    ;

noArgs
    : LPAREN RPAREN
    ;

oneOrMoreArgs
    : LPAREN functionArg (COMMA functionArg)* RPAREN
    ;

functionArg
    : expression
    | expressionType
    ;

expressionType
    : AMP expression
    ;

rawString
    : SINGLE_QUOTE rawStringChar* SINGLE_QUOTE
    ;

rawStringChar
    : RAW_CHAR | preservedEscape | rawStringEscape
    ;

preservedEscape
    : ESCAPE RAW_CHAR
    ;

rawStringEscape
    : ESCAPE (SINGLE_QUOTE | ESCAPE)
    ;

literal
    : BACKTICK jsonText BACKTICK
    ;

jsonText
    : WS jsonValue WS
    ;

jsonValue
    : FALSE
    | NULL
    | TRUE
    | jsonObject
    | jsonArray
    | jsonNumber
    | jsonString
    ;

jsonObject
    : LBRACE (member (COMMA member)*)? RBRACE
    ;

member
    : jsonString COLON jsonValue
    ;

jsonArray
    : LBRACK (jsonValue (COMMA jsonValue)*)? RBRACK
    ;

jsonNumber
    : MINUS? INT (FRAC)? (EXP)?
    ;

jsonString
    : QUOTE (jsonUnescaped | jsonEscaped)* QUOTE
    ;

jsonUnescaped
    : UNESCAPED_JSON
    ;

jsonEscaped
    : escapedChar | ESCAPE BACKTICK
    ;

escapedChar
    : ESCAPE (QUOTE | ESCAPE | SLASH | 'b' | 'f' | 'n' | 'r' | 't' | 'u' HEX HEX HEX HEX)
    ;

propertyNameExpression
    : unquotedString
    | quotedString
    ;

unquotedString
    : UNQUOTED
    ;

quotedString
    : QUOTE (unescapedChar | escapedChar)* QUOTE
    ;

unescapedChar
    : UNESCAPED
    ;

number
    : MINUS INT | INT
    ;

currentNode
    : AT
    ;

/* LEXER RULES */

DOT           : '.' ;
PIPE          : '|' ;
OR            : '||' ;
AND           : '&&' ;
NOT           : '!' ;
PLUS          : '+' ;
MINUS         : '-' | '–' ;
STAR          : '*' | '×' ;
SLASH         : '/' | '÷' ;
DIV           : '//' ;
MOD           : '%' ;
LT            : '<' ;
LTE           : '<=' ;
EQ            : '==' ;
GTE           : '>=' ;
GT            : '>' ;
NEQ           : '!=' ;
LPAREN        : '(' ;
RPAREN        : ')' ;
LBRACK        : '[' ;
RBRACK        : ']' ;
LBRACE        : '{' ;
RBRACE        : '}' ;
COLON         : ':' ;
COMMA         : ',' ;
QUOTE         : '"' ;
SINGLE_QUOTE  : '\'' ;
ESCAPE        : '\\' ;
AMP           : '&' ;
BACKTICK      : '`' ;
QUESTION      : '?' ;
AT            : '@' ;
LET           : 'let' ;
IN            : 'in' ;
FALSE         : 'false' ;
NULL          : 'null' ;
TRUE          : 'true' ;

INT           : '0' | [1-9] DIGIT* ;
FRAC          : '.' DIGIT+ ;
EXP           : [eE] [+-]? DIGIT+ ;
HEX           : [0-9a-fA-F] ;
DIGIT         : [0-9] ;

UNQUOTED      : [a-zA-Z_][a-zA-Z0-9_]* ;
RAW_CHAR      : ~['\\] ;
UNESCAPED     : [\u0020-\u0021\u0023-\u005B\u005D-\u10FFFF] ;
UNESCAPED_JSON: [\u0020-\u0021\u0023-\u005B\u005D-\u005F\u0061-\u10FFFF] ;

WS            : [ \t\r\n]+ -> skip ;
