// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package edu.wpi.first.wpilibj2.command;

import static edu.wpi.first.wpilibj.util.ErrorMessages.requireNonNullParam;

import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.controller.HolonomicDriveController;
import edu.wpi.first.wpilibj.controller.PIDController;
import edu.wpi.first.wpilibj.controller.ProfiledPIDController;
import edu.wpi.first.wpilibj.controller.SimpleMotorFeedforward;
import edu.wpi.first.wpilibj.geometry.Pose2d;
import edu.wpi.first.wpilibj.geometry.Rotation2d;
import edu.wpi.first.wpilibj.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.kinematics.MecanumDriveKinematics;
import edu.wpi.first.wpilibj.kinematics.MecanumDriveMotorVoltages;
import edu.wpi.first.wpilibj.kinematics.MecanumDriveWheelSpeeds;
import edu.wpi.first.wpilibj.trajectory.Trajectory;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A command that uses two PID controllers ({@link PIDController}) and a ProfiledPIDController
 * ({@link ProfiledPIDController}) to follow a trajectory {@link Trajectory} with a mecanum drive.
 *
 * <p>The command handles trajectory-following, Velocity PID calculations, and feedforwards
 * internally. This is intended to be a more-or-less "complete solution" that can be used by teams
 * without a great deal of controls expertise.
 *
 * <p>Advanced teams seeking more flexibility (for example, those who wish to use the onboard PID
 * functionality of a "smart" motor controller) may use the secondary constructor that omits the PID
 * and feedforward functionality, returning only the raw wheel speeds from the PID controllers.
 *
 * <p>The robot angle controller does not follow the angle given by the trajectory but rather goes
 * to the angle given in the final state of the trajectory.
 */
@SuppressWarnings({"PMD.TooManyFields", "MemberName"})
public class MecanumControllerCommand extends CommandBase {
  private final Timer m_timer = new Timer();
  private final boolean m_usePID;
  private final Trajectory m_trajectory;
  private final Supplier<Pose2d> m_pose;
  private final SimpleMotorFeedforward m_feedforward;
  private final MecanumDriveKinematics m_kinematics;
  private final HolonomicDriveController m_controller;
  private final Supplier<Rotation2d> m_desiredRotation;
  private final double m_maxWheelVelocityMetersPerSecond;
  private final PIDController m_frontLeftController;
  private final PIDController m_rearLeftController;
  private final PIDController m_frontRightController;
  private final PIDController m_rearRightController;
  private final Supplier<MecanumDriveWheelSpeeds> m_currentWheelSpeeds;
  private final Consumer<MecanumDriveMotorVoltages> m_outputDriveVoltages;
  private final Consumer<MecanumDriveWheelSpeeds> m_outputWheelSpeeds;
  private MecanumDriveWheelSpeeds m_prevSpeeds;
  private double m_prevTime;

  /**
   * Constructs a new MecanumControllerCommand that when executed will follow the provided
   * trajectory. PID control and feedforward are handled internally. Outputs are scaled from -12 to
   * 12 as a voltage output to the motor.
   *
   * <p>Note: The controllers will *not* set the outputVolts to zero upon completion of the path
   * this is left to the user, since it is not appropriate for paths with nonstationary endstates.
   *
   * @param trajectory The trajectory to follow.
   * @param pose A function that supplies the robot pose - use one of the odometry classes to
   *     provide this.
   * @param feedforward The feedforward to use for the drivetrain.
   * @param kinematics The kinematics for the robot drivetrain.
   * @param xController The Trajectory Tracker PID controller for the robot's x position.
   * @param yController The Trajectory Tracker PID controller for the robot's y position.
   * @param thetaController The Trajectory Tracker PID controller for angle for the robot.
   * @param desiredRotation The angle that the robot should be facing. This is sampled at each time
   *     step.
   * @param maxWheelVelocityMetersPerSecond The maximum velocity of a drivetrain wheel.
   * @param frontLeftController The front left wheel velocity PID.
   * @param rearLeftController The rear left wheel velocity PID.
   * @param frontRightController The front right wheel velocity PID.
   * @param rearRightController The rear right wheel velocity PID.
   * @param currentWheelSpeeds A MecanumDriveWheelSpeeds object containing the current wheel speeds.
   * @param outputDriveVoltages A MecanumDriveMotorVoltages object containing the output motor
   *     voltages.
   * @param requirements The subsystems to require.
   */
  @SuppressWarnings({"PMD.ExcessiveParameterList", "ParameterName"})
  public MecanumControllerCommand(
      Trajectory trajectory,
      Supplier<Pose2d> pose,
      SimpleMotorFeedforward feedforward,
      MecanumDriveKinematics kinematics,
      PIDController xController,
      PIDController yController,
      ProfiledPIDController thetaController,
      Supplier<Rotation2d> desiredRotation,
      double maxWheelVelocityMetersPerSecond,
      PIDController frontLeftController,
      PIDController rearLeftController,
      PIDController frontRightController,
      PIDController rearRightController,
      Supplier<MecanumDriveWheelSpeeds> currentWheelSpeeds,
      Consumer<MecanumDriveMotorVoltages> outputDriveVoltages,
      Subsystem... requirements) {
    m_trajectory = requireNonNullParam(trajectory, "trajectory", "MecanumControllerCommand");
    m_pose = requireNonNullParam(pose, "pose", "MecanumControllerCommand");
    m_feedforward = requireNonNullParam(feedforward, "feedforward", "MecanumControllerCommand");
    m_kinematics = requireNonNullParam(kinematics, "kinematics", "MecanumControllerCommand");

    m_controller =
        new HolonomicDriveController(
            requireNonNullParam(xController, "xController", "SwerveControllerCommand"),
            requireNonNullParam(yController, "xController", "SwerveControllerCommand"),
            requireNonNullParam(thetaController, "thetaController", "SwerveControllerCommand"));

    m_desiredRotation =
        requireNonNullParam(desiredRotation, "desiredRotation", "MecanumControllerCommand");

    m_maxWheelVelocityMetersPerSecond =
        requireNonNullParam(
            maxWheelVelocityMetersPerSecond,
            "maxWheelVelocityMetersPerSecond",
            "MecanumControllerCommand");

    m_frontLeftController =
        requireNonNullParam(frontLeftController, "frontLeftController", "MecanumControllerCommand");
    m_rearLeftController =
        requireNonNullParam(rearLeftController, "rearLeftController", "MecanumControllerCommand");
    m_frontRightController =
        requireNonNullParam(
            frontRightController, "frontRightController", "MecanumControllerCommand");
    m_rearRightController =
        requireNonNullParam(rearRightController, "rearRightController", "MecanumControllerCommand");

    m_currentWheelSpeeds =
        requireNonNullParam(currentWheelSpeeds, "currentWheelSpeeds", "MecanumControllerCommand");

    m_outputDriveVoltages =
        requireNonNullParam(outputDriveVoltages, "outputDriveVoltages", "MecanumControllerCommand");

    m_outputWheelSpeeds = null;

    m_usePID = true;

    addRequirements(requirements);
  }

  /**
   * Constructs a new MecanumControllerCommand that when executed will follow the provided
   * trajectory. PID control and feedforward are handled internally. Outputs are scaled from -12 to
   * 12 as a voltage output to the motor.
   *
   * <p>Note: The controllers will *not* set the outputVolts to zero upon completion of the path
   * this is left to the user, since it is not appropriate for paths with nonstationary endstates.
   *
   * <p>Note 2: The final rotation of the robot will be set to the rotation of the final pose in the
   * trajectory. The robot will not follow the rotations from the poses at each timestep. If
   * alternate rotation behavior is desired, the other constructor with a supplier for rotation
   * should be used.
   *
   * @param trajectory The trajectory to follow.
   * @param pose A function that supplies the robot pose - use one of the odometry classes to
   *     provide this.
   * @param feedforward The feedforward to use for the drivetrain.
   * @param kinematics The kinematics for the robot drivetrain.
   * @param xController The Trajectory Tracker PID controller for the robot's x position.
   * @param yController The Trajectory Tracker PID controller for the robot's y position.
   * @param thetaController The Trajectory Tracker PID controller for angle for the robot.
   * @param maxWheelVelocityMetersPerSecond The maximum velocity of a drivetrain wheel.
   * @param frontLeftController The front left wheel velocity PID.
   * @param rearLeftController The rear left wheel velocity PID.
   * @param frontRightController The front right wheel velocity PID.
   * @param rearRightController The rear right wheel velocity PID.
   * @param currentWheelSpeeds A MecanumDriveWheelSpeeds object containing the current wheel speeds.
   * @param outputDriveVoltages A MecanumDriveMotorVoltages object containing the output motor
   *     voltages.
   * @param requirements The subsystems to require.
   */
  @SuppressWarnings({"PMD.ExcessiveParameterList", "ParameterName"})
  public MecanumControllerCommand(
      Trajectory trajectory,
      Supplier<Pose2d> pose,
      SimpleMotorFeedforward feedforward,
      MecanumDriveKinematics kinematics,
      PIDController xController,
      PIDController yController,
      ProfiledPIDController thetaController,
      double maxWheelVelocityMetersPerSecond,
      PIDController frontLeftController,
      PIDController rearLeftController,
      PIDController frontRightController,
      PIDController rearRightController,
      Supplier<MecanumDriveWheelSpeeds> currentWheelSpeeds,
      Consumer<MecanumDriveMotorVoltages> outputDriveVoltages,
      Subsystem... requirements) {

    this(
        trajectory,
        pose,
        feedforward,
        kinematics,
        xController,
        yController,
        thetaController,
        () ->
            trajectory.getStates().get(trajectory.getStates().size() - 1).poseMeters.getRotation(),
        maxWheelVelocityMetersPerSecond,
        frontLeftController,
        rearLeftController,
        frontRightController,
        rearRightController,
        currentWheelSpeeds,
        outputDriveVoltages,
        requirements);
  }

  /**
   * Constructs a new MecanumControllerCommand that when executed will follow the provided
   * trajectory. The user should implement a velocity PID on the desired output wheel velocities.
   *
   * <p>Note: The controllers will *not* set the outputVolts to zero upon completion of the path -
   * this is left to the user, since it is not appropriate for paths with non-stationary end-states.
   *
   * @param trajectory The trajectory to follow.
   * @param pose A function that supplies the robot pose - use one of the odometry classes to
   *     provide this.
   * @param kinematics The kinematics for the robot drivetrain.
   * @param xController The Trajectory Tracker PID controller for the robot's x position.
   * @param yController The Trajectory Tracker PID controller for the robot's y position.
   * @param thetaController The Trajectory Tracker PID controller for angle for the robot.
   * @param desiredRotation The angle that the robot should be facing. This is sampled at each time
   *     step.
   * @param maxWheelVelocityMetersPerSecond The maximum velocity of a drivetrain wheel.
   * @param outputWheelSpeeds A MecanumDriveWheelSpeeds object containing the output wheel speeds.
   * @param requirements The subsystems to require.
   */
  @SuppressWarnings({"PMD.ExcessiveParameterList", "ParameterName"})
  public MecanumControllerCommand(
      Trajectory trajectory,
      Supplier<Pose2d> pose,
      MecanumDriveKinematics kinematics,
      PIDController xController,
      PIDController yController,
      ProfiledPIDController thetaController,
      Supplier<Rotation2d> desiredRotation,
      double maxWheelVelocityMetersPerSecond,
      Consumer<MecanumDriveWheelSpeeds> outputWheelSpeeds,
      Subsystem... requirements) {
    m_trajectory = requireNonNullParam(trajectory, "trajectory", "MecanumControllerCommand");
    m_pose = requireNonNullParam(pose, "pose", "MecanumControllerCommand");
    m_feedforward = new SimpleMotorFeedforward(0, 0, 0);
    m_kinematics = requireNonNullParam(kinematics, "kinematics", "MecanumControllerCommand");

    m_controller =
        new HolonomicDriveController(
            requireNonNullParam(xController, "xController", "SwerveControllerCommand"),
            requireNonNullParam(yController, "xController", "SwerveControllerCommand"),
            requireNonNullParam(thetaController, "thetaController", "SwerveControllerCommand"));

    m_desiredRotation =
        requireNonNullParam(desiredRotation, "desiredRotation", "MecanumControllerCommand");

    m_maxWheelVelocityMetersPerSecond =
        requireNonNullParam(
            maxWheelVelocityMetersPerSecond,
            "maxWheelVelocityMetersPerSecond",
            "MecanumControllerCommand");

    m_frontLeftController = null;
    m_rearLeftController = null;
    m_frontRightController = null;
    m_rearRightController = null;

    m_currentWheelSpeeds = null;

    m_outputWheelSpeeds =
        requireNonNullParam(outputWheelSpeeds, "outputWheelSpeeds", "MecanumControllerCommand");

    m_outputDriveVoltages = null;

    m_usePID = false;

    addRequirements(requirements);
  }

  /**
   * Constructs a new MecanumControllerCommand that when executed will follow the provided
   * trajectory. The user should implement a velocity PID on the desired output wheel velocities.
   *
   * <p>Note: The controllers will *not* set the outputVolts to zero upon completion of the path -
   * this is left to the user, since it is not appropriate for paths with non-stationary end-states.
   *
   * <p>Note 2: The final rotation of the robot will be set to the rotation of the final pose in the
   * trajectory. The robot will not follow the rotations from the poses at each timestep. If
   * alternate rotation behavior is desired, the other constructor with a supplier for rotation
   * should be used.
   *
   * @param trajectory The trajectory to follow.
   * @param pose A function that supplies the robot pose - use one of the odometry classes to
   *     provide this.
   * @param kinematics The kinematics for the robot drivetrain.
   * @param xController The Trajectory Tracker PID controller for the robot's x position.
   * @param yController The Trajectory Tracker PID controller for the robot's y position.
   * @param thetaController The Trajectory Tracker PID controller for angle for the robot.
   * @param maxWheelVelocityMetersPerSecond The maximum velocity of a drivetrain wheel.
   * @param outputWheelSpeeds A MecanumDriveWheelSpeeds object containing the output wheel speeds.
   * @param requirements The subsystems to require.
   */
  @SuppressWarnings({"PMD.ExcessiveParameterList", "ParameterName"})
  public MecanumControllerCommand(
      Trajectory trajectory,
      Supplier<Pose2d> pose,
      MecanumDriveKinematics kinematics,
      PIDController xController,
      PIDController yController,
      ProfiledPIDController thetaController,
      double maxWheelVelocityMetersPerSecond,
      Consumer<MecanumDriveWheelSpeeds> outputWheelSpeeds,
      Subsystem... requirements) {
    this(
        trajectory,
        pose,
        kinematics,
        xController,
        yController,
        thetaController,
        () ->
            trajectory.getStates().get(trajectory.getStates().size() - 1).poseMeters.getRotation(),
        maxWheelVelocityMetersPerSecond,
        outputWheelSpeeds,
        requirements);
  }

  @Override
  public void initialize() {
    var initialState = m_trajectory.sample(0);

    var initialXVelocity =
        initialState.velocityMetersPerSecond * initialState.poseMeters.getRotation().getCos();
    var initialYVelocity =
        initialState.velocityMetersPerSecond * initialState.poseMeters.getRotation().getSin();

    m_prevSpeeds =
        m_kinematics.toWheelSpeeds(new ChassisSpeeds(initialXVelocity, initialYVelocity, 0.0));

    m_timer.reset();
    m_timer.start();
  }

  @Override
  @SuppressWarnings("LocalVariableName")
  public void execute() {
    double curTime = m_timer.get();
    double dt = curTime - m_prevTime;

    var desiredState = m_trajectory.sample(curTime);

    var targetChassisSpeeds =
        m_controller.calculate(m_pose.get(), desiredState, m_desiredRotation.get());
    var targetWheelSpeeds = m_kinematics.toWheelSpeeds(targetChassisSpeeds);

    targetWheelSpeeds.normalize(m_maxWheelVelocityMetersPerSecond);

    var frontLeftSpeedSetpoint = targetWheelSpeeds.frontLeftMetersPerSecond;
    var rearLeftSpeedSetpoint = targetWheelSpeeds.rearLeftMetersPerSecond;
    var frontRightSpeedSetpoint = targetWheelSpeeds.frontRightMetersPerSecond;
    var rearRightSpeedSetpoint = targetWheelSpeeds.rearRightMetersPerSecond;

    double frontLeftOutput;
    double rearLeftOutput;
    double frontRightOutput;
    double rearRightOutput;

    if (m_usePID) {
      final double frontLeftFeedforward =
          m_feedforward.calculate(
              frontLeftSpeedSetpoint,
              (frontLeftSpeedSetpoint - m_prevSpeeds.frontLeftMetersPerSecond) / dt);

      final double rearLeftFeedforward =
          m_feedforward.calculate(
              rearLeftSpeedSetpoint,
              (rearLeftSpeedSetpoint - m_prevSpeeds.rearLeftMetersPerSecond) / dt);

      final double frontRightFeedforward =
          m_feedforward.calculate(
              frontRightSpeedSetpoint,
              (frontRightSpeedSetpoint - m_prevSpeeds.frontRightMetersPerSecond) / dt);

      final double rearRightFeedforward =
          m_feedforward.calculate(
              rearRightSpeedSetpoint,
              (rearRightSpeedSetpoint - m_prevSpeeds.rearRightMetersPerSecond) / dt);

      frontLeftOutput =
          frontLeftFeedforward
              + m_frontLeftController.calculate(
                  m_currentWheelSpeeds.get().frontLeftMetersPerSecond, frontLeftSpeedSetpoint);

      rearLeftOutput =
          rearLeftFeedforward
              + m_rearLeftController.calculate(
                  m_currentWheelSpeeds.get().rearLeftMetersPerSecond, rearLeftSpeedSetpoint);

      frontRightOutput =
          frontRightFeedforward
              + m_frontRightController.calculate(
                  m_currentWheelSpeeds.get().frontRightMetersPerSecond, frontRightSpeedSetpoint);

      rearRightOutput =
          rearRightFeedforward
              + m_rearRightController.calculate(
                  m_currentWheelSpeeds.get().rearRightMetersPerSecond, rearRightSpeedSetpoint);

      m_outputDriveVoltages.accept(
          new MecanumDriveMotorVoltages(
              frontLeftOutput, frontRightOutput, rearLeftOutput, rearRightOutput));

    } else {
      m_outputWheelSpeeds.accept(
          new MecanumDriveWheelSpeeds(
              frontLeftSpeedSetpoint,
              frontRightSpeedSetpoint,
              rearLeftSpeedSetpoint,
              rearRightSpeedSetpoint));
    }

    m_prevTime = curTime;
    m_prevSpeeds = targetWheelSpeeds;
  }

  @Override
  public void end(boolean interrupted) {
    m_timer.stop();
  }

  @Override
  public boolean isFinished() {
    return m_timer.hasElapsed(m_trajectory.getTotalTimeSeconds());
  }
}
