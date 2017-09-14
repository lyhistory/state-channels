package test;

import org.web3j.crypto.Credentials;

public class Test {
    public static void main(String[] args) throws Exception {
        Credentials credentials = Credentials.create("06e0f2a897cbbe5b5009c3d9cc1ee0b3d29e0d47b1d43125cf71918f24c4cb01");
        System.out.println(credentials.getAddress());
    }
}
