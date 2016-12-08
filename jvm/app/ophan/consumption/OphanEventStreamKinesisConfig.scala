package ophan.consumption

import java.net.InetAddress
import java.util.UUID

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{AWSCredentialsProviderChain, InstanceProfileCredentialsProvider, STSAssumeRoleSessionCredentialsProvider}
import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream.LATEST
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration

object OphanEventStreamKinesisConfig {
  // This application name is used by KCL to store the checkpoint data about how much of the stream you have consumed.
  val applicationName = "ophan-battle-DEV-" + InetAddress.getLocalHost().getHostName // s"${Config.stack}-${Config.app}-kinesis-${Config.stage}"

  // The Kinesis stream you want to consume from
  val streamName = "ophan-events"

  lazy val ophanUserCredentials = new AWSCredentialsProviderChain(
    new STSAssumeRoleSessionCredentialsProvider(
      "arn:aws:iam::021353022223:role/membership-read-ophan-events",
      "roleSessionName"
    ),
    new ProfileCredentialsProvider("ophan")
  )

  val defaultCredentialsProvider = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("membership"),
    new InstanceProfileCredentialsProvider
  )

  // Unique ID for the worker thread
  val workerId = UUID.randomUUID().toString

  val config = new KinesisClientLibConfiguration(
    applicationName,
    streamName,
    ophanUserCredentials,
    defaultCredentialsProvider,
    defaultCredentialsProvider,
    workerId)
    .withInitialLeaseTableReadCapacity(20)
    .withInitialLeaseTableWriteCapacity(40)
    .withIdleTimeBetweenReadsInMillis(2000L)
    .withInitialPositionInStream(LATEST)
    .withRegionName(EU_WEST_1.getName)
}
