package org.wordpress.android.fluxc.mocked

import android.content.Context
import android.net.Uri
import com.android.volley.RequestQueue
import junit.framework.Assert
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.TestUtils
import org.wordpress.android.fluxc.generated.endpoint.WPCOMREST
import org.wordpress.android.fluxc.module.MockedNetworkModule
import org.wordpress.android.fluxc.network.BaseRequest.BaseErrorListener
import org.wordpress.android.fluxc.network.UserAgent
import org.wordpress.android.fluxc.network.discovery.RootWPAPIRestResponse
import org.wordpress.android.fluxc.network.rest.wpcom.BaseWPComRestClient
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest
import org.wordpress.android.fluxc.network.rest.wpcom.WPComGsonRequest.WPComGsonNetworkError
import org.wordpress.android.fluxc.network.rest.wpcom.auth.AccessToken
import org.wordpress.android.fluxc.network.rest.wpcom.jetpacktunnel.WPComJPTunnelGsonRequest
import org.wordpress.android.util.UrlUtils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tests using a Mocked Network app component. Test the network client itself and not the underlying
 * network component(s).
 */
class MockedStack_JetpackTunnelTest : MockedStack_Base() {
    @Inject internal lateinit var jetpackTunnelClient: JetpackTunnelClientForTests

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        mMockedNetworkAppComponent.inject(this)
    }

    fun testErrorResponse() {
        val countDownLatch = CountDownLatch(1)
        val url = "/"

        val request = WPComJPTunnelGsonRequest.buildGetRequest(url, MockedNetworkModule.FAILURE_SITE_ID, mapOf(),
                RootWPAPIRestResponse::class.java,
                { _: RootWPAPIRestResponse? ->
                    throw AssertionError("Unexpected success!")
                },
                BaseErrorListener {
                    error -> run {
                        // Verify that the error response is correctly parsed
                        assertEquals("rest_no_route", (error as WPComGsonNetworkError).apiError)
                        assertEquals("No route was found matching the URL and request method", error.message)
                        countDownLatch.countDown()
                    }
                })

        jetpackTunnelClient.exposedAdd(request)
        Assert.assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))
    }

    fun testSuccessfulGetRequest() {
        val countDownLatch = CountDownLatch(1)
        val url = "/"
        val params = mapOf("context" to "view")

        val request = WPComJPTunnelGsonRequest.buildGetRequest(url, 567, params,
                RootWPAPIRestResponse::class.java,
                { response: RootWPAPIRestResponse? ->
                    run {
                        // Verify that the successful response is correctly parsed
                        assertTrue(response?.namespaces?.contains("wp/v2")!!)
                        countDownLatch.countDown()
                    }
                },
                BaseErrorListener {
                    error -> run {
                        throw AssertionError("Unexpected BaseNetworkError: "
                                + (error as WPComGsonNetworkError).apiError + " - " + error.message)
                    }
                })

        // Verify that the request was built and wrapped as expected
        assertEquals(WPCOMREST.jetpack_blogs.site(567).rest_api.urlV1_1, UrlUtils.removeQuery(request?.url))
        val parsedUri = Uri.parse(request?.url)
        assertEquals(2, parsedUri.queryParameterNames.size)
        assertEquals("/&_method=get&context=view", parsedUri.getQueryParameter("path"))
        assertEquals("true", parsedUri.getQueryParameter("json"))

        // The wrapped GET request should have no body
        val bodyField = request!!::class.java.superclass.getDeclaredField("mBody")
        bodyField.isAccessible = true
        assertNull(bodyField.get(request))

        jetpackTunnelClient.exposedAdd(request)
        Assert.assertTrue(countDownLatch.await(TestUtils.DEFAULT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS))
    }

    @Singleton
    class JetpackTunnelClientForTests @Inject constructor(appContext: Context, dispatcher: Dispatcher,
                                                          requestQueue: RequestQueue, accessToken: AccessToken,
                                                          userAgent: UserAgent
    ) : BaseWPComRestClient(appContext, dispatcher, requestQueue, accessToken, userAgent) {
        /**
         * Wraps and exposes the protected [add] method so that tests can add requests directly.
         */
        fun <T> exposedAdd(request: WPComGsonRequest<T>?) { add(request) }
    }
}
