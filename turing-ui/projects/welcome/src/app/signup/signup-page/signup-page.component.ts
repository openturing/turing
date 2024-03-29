﻿import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { TurSignupService } from '../../_services/signup.service';
import { User } from '../../_models/user';
//import { NotifierService } from 'angular-notifier';
import { Observable } from 'rxjs';
import { UserService } from '../../_services';

@Component({ templateUrl: 'signup-page.component.html' })
export class TurSignupPageComponent implements OnInit {
  loginForm!: UntypedFormGroup;
  loading = false;
  submitted = false;
  returnUrl!: string;
  private user: Observable<User>;
  error = '';

  constructor(
   // private readonly notifier: NotifierService,
    private formBuilder: UntypedFormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private signupService: TurSignupService,
    private userService: UserService
  ) {
    this.user = this.userService.getStructure();
  }

  ngOnInit() {
    this.loginForm = this.formBuilder.group({
      email: ['', Validators.required],
      username: ['', Validators.required],
      password: ['', Validators.required]
    });
  }

  // convenience getter for easy access to form fields
  get f() { return this.loginForm.controls; }

  onSubmit() {
    this.submitted = true;

    // stop here if form is invalid
    if (this.loginForm.invalid) {
      return;
    }

    this.loading = true;
    this.signupService.signup(this.f.email.value, this.f.username.value, this.f.password.value)
    .subscribe(
      data => {
        window.location.href = '/welcome';
      },
      error => {
        this.error = error;
        this.loading = false;
      });
  }
}
