/*
 *  Copyright 2017-2024 Adobe.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.adobe.testing.s3mock;

import static com.adobe.testing.s3mock.BucketNameFilter.BUCKET_ATTRIBUTE;
import static com.adobe.testing.s3mock.util.AwsHttpHeaders.X_AMZ_BUCKET_OBJECT_LOCK_ENABLED;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.CONTINUATION_TOKEN;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.ENCODING_TYPE;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.KEY_MARKER;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.LIFECYCLE;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.LIST_TYPE_V2;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.LOCATION;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.MAX_KEYS;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.NOT_LIFECYCLE;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.NOT_LIST_TYPE;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.NOT_LOCATION;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.NOT_OBJECT_LOCK;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.NOT_PREFIX;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.NOT_UPLOADS;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.NOT_VERSIONS;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.OBJECT_LOCK;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.START_AFTER;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.VERSIONS;
import static com.adobe.testing.s3mock.util.AwsHttpParameters.VERSION_ID_MARKER;
import static org.springframework.http.MediaType.APPLICATION_XML_VALUE;

import com.adobe.testing.s3mock.dto.BucketLifecycleConfiguration;
import com.adobe.testing.s3mock.dto.BucketName;
import com.adobe.testing.s3mock.dto.ListAllMyBucketsResult;
import com.adobe.testing.s3mock.dto.ListBucketResult;
import com.adobe.testing.s3mock.dto.ListBucketResultV2;
import com.adobe.testing.s3mock.dto.ListVersionsResult;
import com.adobe.testing.s3mock.dto.LocationConstraint;
import com.adobe.testing.s3mock.dto.ObjectLockConfiguration;
import com.adobe.testing.s3mock.service.BucketService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import software.amazon.awssdk.regions.Region;

/**
 * Handles requests related to buckets.
 */
@CrossOrigin(origins = "*", exposedHeaders = "*")
@Controller
@RequestMapping("${com.adobe.testing.s3mock.contextPath:}")
public class BucketController {
  private final BucketService bucketService;
  private final Region region;

  public BucketController(BucketService bucketService, Region region) {
    this.bucketService = bucketService;
    this.region = region;
  }

  //================================================================================================
  // /
  //================================================================================================

  /**
   * List all existing buckets.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListBuckets.html">API Reference</a>
   *
   * @return List of all Buckets
   */
  @GetMapping(
      value = "/",
      params = {
        NOT_PREFIX
      },
      produces = APPLICATION_XML_VALUE
  )
  public ResponseEntity<ListAllMyBucketsResult> listBuckets() {
    var listAllMyBucketsResult = bucketService.listBuckets();
    return ResponseEntity.ok(listAllMyBucketsResult);
  }

  //================================================================================================
  // /{bucketName:.+}
  //================================================================================================

  /**
   * Create a bucket if the name matches a simplified version of the bucket naming rules.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucketnamingrules.html">API Reference Bucket Naming</a>
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_CreateBucket.html">API Reference</a>
   *
   * @param bucketName name of the bucket that should be created.
   *
   * @return 200 OK if creation was successful.
   */
  @PutMapping(
      value = {
          //AWS SDK V2 pattern
          "/{bucketName:.+}",
          //AWS SDK V1 pattern
          "/{bucketName:.+}/"
      },
      params = {
          NOT_OBJECT_LOCK,
          NOT_LIFECYCLE
      }
  )
  public ResponseEntity<Void> createBucket(@PathVariable final String bucketName,
      @RequestHeader(value = X_AMZ_BUCKET_OBJECT_LOCK_ENABLED,
          required = false, defaultValue = "false") boolean objectLockEnabled,
      @RequestAttribute(BUCKET_ATTRIBUTE) BucketName bucket) {
    //TODO: does subdomain access work for #createBucket in S3?
    assert bucketName.equals(bucket.getName());
    bucketService.verifyBucketNameIsAllowed(bucketName);
    bucketService.verifyBucketDoesNotExist(bucketName);
    bucketService.createBucket(bucketName, objectLockEnabled);
    return ResponseEntity.ok().build();
  }

  /**
   * Check if a bucket exists.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_HeadBucket.html">API Reference</a>
   *
   * @param bucketName name of the Bucket.
   *
   * @return 200 if it exists; 404 if not found.
   */
  @RequestMapping(
      value = {
          //AWS SDK V2 pattern
          "/{bucketName:.+}",
          //AWS SDK V1 pattern
          "/{bucketName:.+}/"
      },
      method = RequestMethod.HEAD
  )
  public ResponseEntity<Void> headBucket(@PathVariable final String bucketName,
      @RequestAttribute(BUCKET_ATTRIBUTE) BucketName bucket) {
    assert bucketName.equals(bucket.getName());
    bucketService.verifyBucketExists(bucketName);
    //return bucket region
    //return bucket location
    return ResponseEntity.ok().build();
  }

  /**
   * Delete a bucket.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteBucket.html">API Reference</a>
   *
   * @param bucketName name of the Bucket.
   *
   * @return 204 if Bucket was deleted; 404 if not found
   */
  @DeleteMapping(
      value = {
          //AWS SDK V2 pattern
          "/{bucketName:.+}",
          //AWS SDK V1 pattern
          "/{bucketName:.+}/"
      },
      params = {
          NOT_LIFECYCLE
      }
  )
  public ResponseEntity<Void> deleteBucket(@PathVariable String bucketName,
      @RequestAttribute(BUCKET_ATTRIBUTE) BucketName bucket) {
    assert bucketName.equals(bucket.getName());
    bucketService.verifyBucketExists(bucketName);
    bucketService.verifyBucketIsEmpty(bucketName);
    bucketService.deleteBucket(bucketName);
    return ResponseEntity.noContent().build();
  }

  /**
   * Get ObjectLockConfiguration of a bucket.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObjectLockConfiguration.html">API Reference</a>
   *
   * @param bucketName name of the Bucket.
   *
   * @return 200, ObjectLockConfiguration
   */
  @GetMapping(
      value = {
          //AWS SDK V2 pattern
          "/{bucketName:.+}",
          //AWS SDK V1 pattern
          "/{bucketName:.+}/"
      },
      params = {
          OBJECT_LOCK,
          NOT_LIST_TYPE
      },
      produces = APPLICATION_XML_VALUE
  )
  public ResponseEntity<ObjectLockConfiguration> getObjectLockConfiguration(
      @PathVariable String bucketName,
      @RequestAttribute(BUCKET_ATTRIBUTE) BucketName bucket) {
    assert bucketName.equals(bucket.getName());
    bucketService.verifyBucketExists(bucketName);
    var configuration = bucketService.getObjectLockConfiguration(bucketName);
    return ResponseEntity.ok(configuration);
  }

  /**
   * Put ObjectLockConfiguration of a bucket.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutObjectLockConfiguration.html">API Reference</a>
   *
   * @param bucketName name of the Bucket.
   *
   * @return 200, ObjectLockConfiguration
   */
  @PutMapping(
      value = {
          //AWS SDK V2 pattern
          "/{bucketName:.+}",
          //AWS SDK V1 pattern
          "/{bucketName:.+}/"
      },
      params = {
          OBJECT_LOCK
      },
      consumes = APPLICATION_XML_VALUE
  )
  public ResponseEntity<Void> putObjectLockConfiguration(
      @PathVariable String bucketName,
      @RequestBody ObjectLockConfiguration configuration,
      @RequestAttribute(BUCKET_ATTRIBUTE) BucketName bucket) {
    assert bucketName.equals(bucket.getName());
    bucketService.verifyBucketExists(bucketName);
    bucketService.setObjectLockConfiguration(bucketName, configuration);
    return ResponseEntity.ok().build();
  }

  /**
   * Get BucketLifecycleConfiguration of a bucket.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetBucketLifecycleConfiguration.html">API Reference</a>
   *
   * @param bucketName name of the Bucket.
   *
   * @return 200, ObjectLockConfiguration
   */
  @GetMapping(
      value = {
          //AWS SDK V2 pattern
          "/{bucketName:.+}",
          //AWS SDK V1 pattern
          "/{bucketName:.+}/"
      },
      params = {
          LIFECYCLE,
          NOT_LIST_TYPE
      },
      produces = APPLICATION_XML_VALUE
  )
  public ResponseEntity<BucketLifecycleConfiguration> getBucketLifecycleConfiguration(
      @PathVariable String bucketName,
      @RequestAttribute(BUCKET_ATTRIBUTE) BucketName bucket) {
    assert bucketName.equals(bucket.getName());
    bucketService.verifyBucketExists(bucketName);
    var configuration = bucketService.getBucketLifecycleConfiguration(bucketName);
    return ResponseEntity.ok(configuration);
  }

  /**
   * Put BucketLifecycleConfiguration of a bucket.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutBucketLifecycleConfiguration.html">API Reference</a>
   *
   * @param bucketName name of the Bucket.
   *
   * @return 200, ObjectLockConfiguration
   */
  @PutMapping(
      value = {
          //AWS SDK V2 pattern
          "/{bucketName:.+}",
          //AWS SDK V1 pattern
          "/{bucketName:.+}/"
      },
      params = {
          LIFECYCLE
      },
      consumes = APPLICATION_XML_VALUE
  )
  public ResponseEntity<Void> putBucketLifecycleConfiguration(
      @PathVariable String bucketName,
      @RequestBody BucketLifecycleConfiguration configuration,
      @RequestAttribute(BUCKET_ATTRIBUTE) BucketName bucket) {
    assert bucketName.equals(bucket.getName());
    bucketService.verifyBucketExists(bucketName);
    bucketService.setBucketLifecycleConfiguration(bucketName, configuration);
    return ResponseEntity.ok().build();
  }

  /**
   * Delete BucketLifecycleConfiguration of a bucket.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteBucketLifecycle.html">API Reference</a>
   *
   * @param bucketName name of the Bucket.
   *
   * @return 200, ObjectLockConfiguration
   */
  @DeleteMapping(
      value = {
          //AWS SDK V2 pattern
          "/{bucketName:.+}",
          //AWS SDK V1 pattern
          "/{bucketName:.+}/"
      },
      params = {
          LIFECYCLE
      }
  )
  public ResponseEntity<Void> deleteBucketLifecycleConfiguration(
      @PathVariable String bucketName,
      @RequestAttribute(BUCKET_ATTRIBUTE) BucketName bucket) {
    assert bucketName.equals(bucket.getName());
    bucketService.verifyBucketExists(bucketName);
    bucketService.deleteBucketLifecycleConfiguration(bucketName);
    return ResponseEntity.noContent().build();
  }

  /**
   * Get location of a bucket.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetBucketLocation.html">API Reference</a>
   *
   * @param bucketName name of the Bucket.
   *
   * @return 200, LocationConstraint
   */
  @GetMapping(
      value = "/{bucketName:.+}",
      params = {
          LOCATION
      }
  )
  public ResponseEntity<LocationConstraint> getBucketLocation(
      @PathVariable String bucketName) {
    bucketService.verifyBucketExists(bucketName);
    return ResponseEntity.ok(new LocationConstraint(region));
  }

  /**
   * Retrieve list of objects of a bucket.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjects.html">API Reference</a>
   *
   * @param bucketName {@link String} set bucket name
   * @param prefix {@link String} find object names they start with prefix
   * @param encodingType whether to use URL encoding (encodingtype="url") or not
   *
   * @return {@link ListBucketResult} a list of objects in Bucket
   * @deprecated Long since replaced by ListObjectsV2, {@see #listObjectsInsideBucketV2}
   */
  @GetMapping(
      value = {
          "/",
          //AWS SDK V2 pattern
          "/{bucketName:.+}",
          //AWS SDK V1 pattern
          "/{bucketName:.+}/"
      },
      params = {
          NOT_UPLOADS,
          NOT_OBJECT_LOCK,
          NOT_LIST_TYPE,
          NOT_LIFECYCLE,
          NOT_LOCATION,
          NOT_VERSIONS
      },
      produces = APPLICATION_XML_VALUE
  )
  @Deprecated(since = "2.12.2", forRemoval = true)
  public <T> T listObjects(
      @PathVariable(required = false) String bucketName,
      @RequestParam(required = false) String prefix,
      @RequestParam(required = false) String delimiter,
      @RequestParam(required = false) String marker,
      @RequestParam(name = ENCODING_TYPE, required = false) String encodingType,
      @RequestParam(name = MAX_KEYS, defaultValue = "1000", required = false) Integer maxKeys,
      @RequestAttribute(name = BUCKET_ATTRIBUTE, required = false) BucketName bucket) {
    if (bucket == null) {
      return (T) listBuckets();
    }
    bucketName = bucket.getName();
    bucketService.verifyBucketExists(bucketName);
    bucketService.verifyMaxKeys(maxKeys);
    bucketService.verifyEncodingType(encodingType);
    var listBucketResult = bucketService.listObjectsV1(bucketName, prefix, delimiter,
        marker, encodingType, maxKeys);
    return (T) ResponseEntity.ok(listBucketResult);
  }

  /**
   * Retrieve list of objects of a bucket.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectsV2.html">API Reference</a>
   *
   * @param bucketName {@link String} set bucket name
   * @param prefix {@link String} find object names they start with prefix
   * @param startAfter {@link String} return key names after a specific object key in your key
   *     space
   * @param maxKeys {@link Integer} set maximum number of keys to be returned
   * @param continuationToken {@link String} pagination token returned by previous request
   *
   * @return {@link ListBucketResultV2} a list of objects in Bucket
   */
  @GetMapping(
      value = {
          "/",
          //AWS SDK V2 pattern
          "/{bucketName:.+}",
          //AWS SDK V1 pattern
          "/{bucketName:.+}/"
      },
      params = {
          LIST_TYPE_V2
      },
      produces = APPLICATION_XML_VALUE
  )
  public ResponseEntity<ListBucketResultV2> listObjectsV2(
      @PathVariable(required = false) String bucketName,
      @RequestParam(required = false) String prefix,
      @RequestParam(required = false) String delimiter,
      @RequestParam(name = ENCODING_TYPE, required = false) String encodingType,
      @RequestParam(name = START_AFTER, required = false) String startAfter,
      @RequestParam(name = MAX_KEYS, defaultValue = "1000", required = false) Integer maxKeys,
      @RequestParam(name = CONTINUATION_TOKEN, required = false) String continuationToken,
      @RequestAttribute(BUCKET_ATTRIBUTE) BucketName bucket) {
    bucketName = bucket.getName();
    bucketService.verifyBucketExists(bucketName);
    bucketService.verifyMaxKeys(maxKeys);
    bucketService.verifyEncodingType(encodingType);
    var listBucketResultV2 =
        bucketService.listObjectsV2(bucketName, prefix, delimiter, encodingType, startAfter,
            maxKeys, continuationToken);

    return ResponseEntity.ok(listBucketResultV2);
  }

  /**
   * Retrieve list of versions of an object of a bucket.
   * <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_ListObjectVersions.html">API Reference</a>
   *
   * @param bucketName {@link String} set bucket name
   * @param prefix {@link String} find object names they start with prefix
   * @param startAfter {@link String} return key names after a specific object key in your key
   *     space
   * @param maxKeys {@link Integer} set maximum number of keys to be returned
   * @param continuationToken {@link String} pagination token returned by previous request
   *
   * @return {@link ListVersionsResult} a list of objects in Bucket
   */
  @GetMapping(
      value = {
          //AWS SDK V2 pattern
          "/{bucketName:.+}",
          //AWS SDK V1 pattern
          "/{bucketName:.+}/"
      },
      params = {
          VERSIONS
      },
      produces = APPLICATION_XML_VALUE
  )
  public ResponseEntity<ListVersionsResult> listObjectVersions(
      @PathVariable String bucketName,
      @RequestParam(required = false) String prefix,
      @RequestParam(required = false) String delimiter,
      @RequestParam(name = KEY_MARKER, required = false) String keyMarker,
      @RequestParam(name = VERSION_ID_MARKER, required = false) String versionIdMarker,
      @RequestParam(name = ENCODING_TYPE, required = false) String encodingType,
      @RequestParam(name = START_AFTER, required = false) String startAfter,
      @RequestParam(name = MAX_KEYS, defaultValue = "1000", required = false) Integer maxKeys,
      @RequestParam(name = CONTINUATION_TOKEN, required = false) String continuationToken) {
    bucketService.verifyBucketExists(bucketName);
    bucketService.verifyMaxKeys(maxKeys);
    bucketService.verifyEncodingType(encodingType);
    var listVersionsResult =
        bucketService.listVersions(bucketName, prefix, delimiter, encodingType, startAfter,
            maxKeys, continuationToken, keyMarker, versionIdMarker);

    return ResponseEntity.ok(listVersionsResult);
  }
}
