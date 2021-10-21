public class OAuthError {
    String error;
    String error_description;

    OAuthError(String error, String error_description) {
        this.error = error;
        this.error_description = error_description;
    }
}