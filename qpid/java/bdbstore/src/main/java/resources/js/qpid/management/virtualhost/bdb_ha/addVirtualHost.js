/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
define(["dojo/_base/xhr",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/_base/window",
        "dijit/registry",
        "dojo/parser",
        "dojo/_base/array",
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, parser, array) {
        return {
            show: function() {

                var node = dom.byId("addVirtualHost.typeSpecificDiv");
                var that = this;

                array.forEach(registry.toArray(),
                              function(item) {
                                  if(item.id.substr(0,27) == "formAddVirtualHost.specific") {
                                      item.destroyRecursive();
                                  }
                              });

                xhr.get({url: "virtualhost/bdb_ha/add.html",
                     sync: true,
                     load:  function(data) {
                                node.innerHTML = data;
                                parser.parse(node);
                     }});
            }
        };
    });
