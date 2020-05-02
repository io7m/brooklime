/*
 * Copyright © 2020 Mark Raynsford <code@io7m.com> http://io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.io7m.brooklime.cmdline.internal;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.io7m.brooklime.api.BLException;
import com.io7m.brooklime.api.BLNexusClientConfiguration;
import com.io7m.brooklime.api.BLNexusClientProviderType;
import com.io7m.brooklime.api.BLNexusClientType;
import com.io7m.brooklime.api.BLStagingProfileRepository;
import com.io7m.brooklime.api.BLStagingRepositoryClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;

@Parameters(commandDescription = "Close an existing staging repository")
public final class BLCommandCloseStagingRepository extends BLCommandRoot
{
  private static final Logger LOG =
    LoggerFactory.getLogger(BLCommandCloseStagingRepository.class);

  @Parameter(
    names = "--user",
    description = "The Nexus user name",
    required = true
  )
  private String userName;

  @Parameter(
    names = "--password",
    description = "The Nexus password",
    required = true
  )
  private String password;

  @Parameter(
    names = "--stagingProfileId",
    description = "The Nexus staging profile id",
    required = true
  )
  private String stagingProfileId;

  @Parameter(
    names = "--baseURI",
    description = "The Nexus URI",
    required = false
  )
  private URI baseURI = URI.create("https://oss.sonatype.org:443/");

  @Parameter(
    names = "--repository",
    description = "The staging repository ID",
    required = true
  )
  private String stagingRepositoryId;

  public BLCommandCloseStagingRepository()
  {

  }

  @Override
  public Status execute()
    throws Exception
  {
    if (super.execute() == Status.FAILURE) {
      return Status.FAILURE;
    }

    final BLNexusClientProviderType clients = BLServices.findClients();

    final BLNexusClientConfiguration clientConfiguration =
      BLNexusClientConfiguration.builder()
        .setApplicationVersion(BLServices.findApplicationVersion())
        .setUserName(this.userName)
        .setPassword(this.password)
        .setBaseURI(this.baseURI)
        .setStagingProfileId(this.stagingProfileId)
        .build();

    try (BLNexusClientType client = clients.createClient(clientConfiguration)) {
      client.stagingRepositoryClose(
        BLStagingRepositoryClose.builder()
          .addStagingRepositories(this.stagingRepositoryId)
          .build()
      );

      while (true) {
        LOG.debug("waiting for repository to close...");

        try {
          Thread.sleep(2_000L);
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        }

        final Optional<BLStagingProfileRepository> repositoryOpt =
          client.stagingRepositoryGet(this.stagingRepositoryId);

        if (!repositoryOpt.isPresent()) {
          throw new BLException(
            String.format(
              "The repository %s unexpectedly vanished!",
              this.stagingRepositoryId)
          );
        }

        final BLStagingProfileRepository repository = repositoryOpt.get();
        if (!repository.transitioning()) {
          final String state = repository.type();
          switch (state.toUpperCase()) {
            case "CLOSED": {
              return Status.SUCCESS;
            }
            default: {
              throw new BLException(
                String.format(
                  "The repository %s is no longer transitioning, but unexpectedly ended up in state %s",
                  this.stagingRepositoryId,
                  state)
              );
            }
          }
        }
      }
    }
  }
}
