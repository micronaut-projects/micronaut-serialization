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
package io.micronaut.serde.xml.tck;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public class CustomBean {

    private String A1;
    private List<String> C1;
    private String B1;

    public CustomBean() {
    }

    public CustomBean(String a1, List<String> c1, String b1) {
        A1 = a1;
        C1 = c1;
        B1 = b1;
    }

    public String getA1() {
        return A1;
    }

    public void setA1(String a1) {
        A1 = a1;
    }

    public List<String> getC1() {
        return C1;
    }

    public void setC1(List<String> c1) {
        C1 = c1;
    }

    public String getB1() {
        return B1;
    }

    public void setB1(String b1) {
        B1 = b1;
    }
}
