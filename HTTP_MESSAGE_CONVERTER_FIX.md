# HTTP Message Converter Fix

## Problem
The application was throwing the following error:
```
Could not extract response: no suitable HttpMessageConverter found for response type [interface java.util.Map] and content type [text/plain]
```

## Root Cause
The error occurred in the `ChatController` when making REST API calls to the Python AI service. The RestTemplate was not properly configured to handle responses where:
- The content-type header might be `text/plain` instead of `application/json`
- The actual response body contains valid JSON but the content-type doesn't match
- No appropriate message converter was available to parse the response

## Solution

### 1. Created RestTemplate Configuration (`RestTemplateConfig.java`)
Added a new Spring configuration class that:
- Creates a RestTemplate bean with custom message converters
- Configures `MappingJackson2HttpMessageConverter` to accept multiple content types:
  - `application/json` (standard JSON)
  - `text/plain` (plain text that may contain JSON)
  - `text/html` (HTML that may contain JSON)
  - `application/*+json` (any JSON variants)
  - `text/*` (any text content)
- Places the converter at the beginning of the converter list for priority

```java
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        List<MediaType> supportedMediaTypes = Arrays.asList(
            MediaType.APPLICATION_JSON,
            MediaType.TEXT_PLAIN,
            MediaType.TEXT_HTML,
            new MediaType("application", "*+json"),
            new MediaType("text", "*")
        );
        jsonConverter.setSupportedMediaTypes(supportedMediaTypes);
        restTemplate.getMessageConverters().add(0, jsonConverter);
        
        return restTemplate;
    }
}
```

### 2. Updated ChatController
Modified `ChatController.java` to:
- **Use dependency injection**: Inject the configured RestTemplate bean instead of creating a new instance
- **Added @Autowired constructor**: Properly wire the RestTemplate bean from Spring context
- **Enhanced error handling**: Added `RestClientException` catch blocks to all methods to properly handle:
  - Message conversion errors
  - Connection errors
  - Other REST client issues

### 3. Improved Error Handling
Added `RestClientException` handling to all proxy methods:
- `/api/chat` (POST)
- `/api/cancel` (POST)
- `/api/history/{sessionId}` (GET)
- `/api/history/{sessionId}` (DELETE)
- `/api/ai-health` (GET)

Each method now catches:
1. `HttpClientErrorException` - 4xx errors from AI service
2. `HttpServerErrorException` - 5xx errors from AI service
3. `RestClientException` - Message conversion and communication errors
4. `Exception` - Any other unexpected errors

## Benefits
1. **Robust content-type handling**: Can parse JSON responses regardless of content-type header
2. **Better error messages**: Clear logging and error responses for debugging
3. **Spring best practices**: Uses dependency injection and centralized configuration
4. **Fault tolerance**: Gracefully handles various error scenarios

## Files Changed
1. **NEW**: `/workspace/java-websocket-server/src/main/java/com/demo/websocket/config/RestTemplateConfig.java`
   - Created new configuration class for RestTemplate

2. **MODIFIED**: `/workspace/java-websocket-server/src/main/java/com/demo/websocket/controller/ChatController.java`
   - Updated constructor to use @Autowired and inject RestTemplate bean
   - Added RestClientException handling to all methods
   - Imported org.springframework.web.client.RestClientException

## Testing
After deploying these changes:
1. All REST API proxy calls should work properly
2. The message converter error should no longer occur
3. Better error messages will be logged for debugging

## Next Steps
If the error persists after this fix, check:
1. The Python AI service is returning valid JSON in response bodies
2. Network connectivity between Java and Python services
3. The `ai.service.url` configuration is correct
4. Review logs for the specific error messages now being captured
