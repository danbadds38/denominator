package denominator;

import com.google.common.base.Supplier;

import org.testng.annotations.Test;

import denominator.Credentials.ListCredentials;

import static org.testng.Assert.assertEquals;

@Test
public class CredentialsTest {

  public void testTwoPartCredentialsEqualsHashCode() {
    assertEquals(ListCredentials.from("user", "pass"), ListCredentials.from("user", "pass"));
    assertEquals(ListCredentials.from("user", "pass").hashCode(),
                 ListCredentials.from("user", "pass").hashCode());
  }

  public void testThreePartCredentialsEqualsHashCode() {
    assertEquals(ListCredentials.from("customer", "user", "pass"),
                 ListCredentials.from("customer", "user", "pass"));
    assertEquals(ListCredentials.from("customer", "user", "pass").hashCode(),
                 ListCredentials.from("customer", "user", "pass").hashCode());
  }

  public void testHowToConvertSomethingLikeAmazon() {
    final AWSCredentialsProvider provider = new AWSCredentialsProvider();
    Supplier<Credentials> converter = new Supplier<Credentials>() {
      public Credentials get() {
        AWSCredentials awsCreds = provider.getCredentials();
        return ListCredentials.from(awsCreds.getAWSAccessKeyId(), awsCreds.getAWSSecretKey());
      }
    };
    assertEquals(converter.get(), ListCredentials.from("accessKey", "secretKey"));
  }

  static enum AWSCredentials {
    INSTANCE;

    String getAWSAccessKeyId() {
      return "accessKey";
    }

    String getAWSSecretKey() {
      return "secretKey";
    }
  }

  static class AWSCredentialsProvider {

    AWSCredentials getCredentials() {
      return AWSCredentials.INSTANCE;
    }
  }
}
