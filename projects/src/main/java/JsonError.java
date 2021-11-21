class JsonError {
    String error, error_description;
    public JsonError(String error, String error_description) {
        this.error = error;
        this.error_description = error_description;
    }
    public JsonError(String error) {
        this.error = error;
    }
}