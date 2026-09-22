/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.processor.sourcegen;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the statement switches of the generated serdes for the language the source is generated for.
 *
 * <p>The Java source backend renders a statement switch with {@code case ... ->} labels. Groovy only
 * accepts those labels in a switch expression, so it parses such a switch as an expression and rejects
 * the {@code return} statements the cases contain. The Groovy source therefore gets an if/else chain.</p>
 *
 * @param language The language of the generated source
 */
@Internal
public record SerdeSourceGenSwitches(VisitorContext.Language language) {

    /**
     * Builds a statement that runs the case matching the value.
     *
     * @param value The switched value
     * @param type The type of the value, a primitive or an enum
     * @param localName The name of the local the Groovy source stores a value that is not a variable in
     * @param cases The statements by the matched constant
     * @return The switch statement
     */
    public StatementDef statementSwitch(ExpressionDef value,
                                        TypeDef type,
                                        String localName,
                                        Map<ExpressionDef.Constant, StatementDef> cases) {
        if (language != VisitorContext.Language.GROOVY) {
            return value.asStatementSwitch(type, cases);
        }
        if (value instanceof VariableDef variable) {
            return ifElseChain(variable, cases);
        }
        return value.newLocal(localName, variable -> ifElseChain(variable, cases));
    }

    private static StatementDef ifElseChain(VariableDef variable, Map<ExpressionDef.Constant, StatementDef> cases) {
        List<Map.Entry<ExpressionDef.Constant, StatementDef>> entries = new ArrayList<>(cases.entrySet());
        if (entries.isEmpty()) {
            return StatementDef.multi();
        }
        Map.Entry<ExpressionDef.Constant, StatementDef> lastEntry = entries.getLast();
        StatementDef chain = matches(variable, lastEntry.getKey()).doIf(lastEntry.getValue());
        for (int i = entries.size() - 2; i >= 0; i--) {
            Map.Entry<ExpressionDef.Constant, StatementDef> entry = entries.get(i);
            chain = matches(variable, entry.getKey()).doIfElse(entry.getValue(), chain);
        }
        return chain;
    }

    private static ExpressionDef.ConditionExpressionDef matches(VariableDef variable, ExpressionDef.Constant constant) {
        return variable.equalsReferentially(caseValue(constant));
    }

    private static ExpressionDef caseValue(ExpressionDef.Constant constant) {
        if (constant.type() instanceof ClassTypeDef enumType && constant.value() instanceof String name) {
            return enumType.getStaticField(name, enumType);
        }
        return constant;
    }
}
