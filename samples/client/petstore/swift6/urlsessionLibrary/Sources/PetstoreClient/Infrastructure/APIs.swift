// APIs.swift
//
// Generated by openapi-generator
// https://openapi-generator.tech
//

import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public enum PetstoreClientAPI {}

open class PetstoreClientAPIConfiguration: @unchecked Sendable {
    public var basePath: String
    public var customHeaders: [String: String]
    public var credential: URLCredential?
    public var requestBuilderFactory: RequestBuilderFactory
    public var apiResponseQueue: DispatchQueue
    public var codableHelper: CodableHelper

    /// Configures the range of HTTP status codes that will result in a successful response
    ///
    /// If a HTTP status code is outside of this range the response will be interpreted as failed.
    public var successfulStatusCodeRange: Range<Int>

    public var interceptor: OpenAPIInterceptor

    public init(
        basePath: String = "http://petstore.swagger.io:80/v2",
        customHeaders: [String: String] = [:],
        credential: URLCredential? = nil,
        requestBuilderFactory: RequestBuilderFactory = URLSessionRequestBuilderFactory(),
        apiResponseQueue: DispatchQueue = .main,
        codableHelper: CodableHelper = CodableHelper(),
        successfulStatusCodeRange: Range<Int> = 200..<300,
        interceptor: OpenAPIInterceptor = DefaultOpenAPIInterceptor()
    ) {
        self.basePath = basePath
        self.customHeaders = customHeaders
        self.credential = credential
        self.requestBuilderFactory = requestBuilderFactory
        self.apiResponseQueue = apiResponseQueue
        self.codableHelper = codableHelper
        self.successfulStatusCodeRange = successfulStatusCodeRange
        self.interceptor = interceptor
    }

    public static let shared = PetstoreClientAPIConfiguration()
}

open class RequestBuilder<T>: @unchecked Sendable {
    public var credential: URLCredential?
    public var headers: [String: String]
    public let parameters: [String: Any]?
    public let method: String
    public let URLString: String
    public let requestTask: RequestTask = RequestTask()
    public let requiresAuthentication: Bool
    public let apiConfiguration: PetstoreClientAPIConfiguration

    /// Optional block to obtain a reference to the request's progress instance when available.
    public var onProgressReady: ((Progress) -> Void)?

    required public init(method: String, URLString: String, parameters: [String: Any]?, headers: [String: String] = [:], requiresAuthentication: Bool, apiConfiguration: PetstoreClientAPIConfiguration = PetstoreClientAPIConfiguration.shared) {
        self.method = method
        self.URLString = URLString
        self.parameters = parameters
        self.headers = headers
        self.requiresAuthentication = requiresAuthentication
        self.apiConfiguration = apiConfiguration

        addHeaders(apiConfiguration.customHeaders)
        addCredential()
    }

    open func addHeaders(_ aHeaders: [String: String]) {
        for (header, value) in aHeaders {
            headers[header] = value
        }
    }

    @discardableResult
    open func execute(completion: @Sendable @escaping (_ result: Swift.Result<Response<T>, ErrorResponse>) -> Void) -> RequestTask {
        return requestTask
    }

    public func addHeader(name: String, value: String) -> Self {
        if !value.isEmpty {
            headers[name] = value
        }
        return self
    }

    open func addCredential() {
        credential = apiConfiguration.credential
    }
}

public protocol RequestBuilderFactory {
    func getNonDecodableBuilder<T>() -> RequestBuilder<T>.Type
    func getBuilder<T: Decodable>() -> RequestBuilder<T>.Type
}
