package com.teapotrecords.sbandroid;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class XMLHelper {
  
  static Node getTag(Node root, String name) {
    NodeList nl = root.getChildNodes();
    Node result = null;
    for (int i=0; i<nl.getLength(); i++) {
      if (nl.item(i).getNodeName().equals(name)) {
        result = nl.item(i);
        i=nl.getLength();
      }
    }
    return result;
  }

}
