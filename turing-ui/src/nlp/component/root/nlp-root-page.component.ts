import { Component, OnInit } from '@angular/core';
import { NotifierService } from 'angular-notifier';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'nlp-root-page',
  templateUrl: './nlp-root-page.component.html'
})
export class TurNLPRootPageComponent implements OnInit {

  constructor(private readonly notifier: NotifierService, private router: Router) {

  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }

  imports: [
    RouterModule
  ]
}
