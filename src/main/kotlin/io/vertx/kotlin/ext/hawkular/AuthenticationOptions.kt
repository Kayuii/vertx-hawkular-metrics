package io.vertx.kotlin.ext.hawkular

import io.vertx.ext.hawkular.AuthenticationOptions

/**
 * A function providing a DSL for building [io.vertx.ext.hawkular.AuthenticationOptions] objects.
 *
 * Authentication options.
 *
 * @param enabled  Set whether authentication is enabled. Defaults to <code>false</code>.
 * @param id  Set the identifier used for authentication.
 * @param secret  Set the secret used for authentication.
 *
 * <p/>
 * NOTE: This function has been automatically generated from the [io.vertx.ext.hawkular.AuthenticationOptions original] using Vert.x codegen.
 */
fun AuthenticationOptions(
  enabled: Boolean? = null,
  id: String? = null,
  secret: String? = null): AuthenticationOptions = io.vertx.ext.hawkular.AuthenticationOptions().apply {

  if (enabled != null) {
    this.setEnabled(enabled)
  }
  if (id != null) {
    this.setId(id)
  }
  if (secret != null) {
    this.setSecret(secret)
  }
}

