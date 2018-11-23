package org.ebayopensource.fido.uaf.msg.asm.obj;

public class OIDCTokens {
    public String access_token; //required DOMString      access_token;
    public String token_type; //required DOMString      token_type;
    public String refresh_token; //required DOMString      refresh_token;
    public int expires_in; //required unsigned short expires_in;
    public String id_token; //required DOMString      id_token;
}
